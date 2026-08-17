package com.dvnnfrr.saku.ui.accounts

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.STALE_AFTER
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The accounts screen's arithmetic, on plain lists and no database.
 *
 * Covers every scenario in specs/account-management/spec.md that is a question about
 * numbers: the balance formula and its date clause, the Spendable/Invested/Total fold
 * and its liability sign, the donut's shares, and the staleness rule. The scenarios
 * about persistence — first run, archiving, reconciling — are in AccountsViewModelTest,
 * against a real database.
 */
class AccountsAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val cutover = Instant.parse("2026-08-17T00:00:00Z")
    private val afterCutover = Instant.parse("2026-08-18T12:00:00Z")
    private val now = Instant.parse("2026-08-20T09:00:00Z")

    private var nextId = 1L

    private fun account(
        name: String,
        type: AccountType = AccountType.BANK,
        opening: Long = 0L,
        balance: Long = opening,
        asOf: Instant = cutover,
        archived: Boolean = false,
        lastReconciled: Instant? = null,
        slot: Int = 0,
        id: Long = nextId++,
    ) = Account(
        id = id,
        name = name,
        type = type,
        colorSlot = ColorSlot(slot),
        sortOrder = id.toInt(),
        archived = archived,
        openingBalance = Money(opening),
        openingAsOf = asOf,
        lastReconciled = lastReconciled,
        balance = Money(balance),
    )

    private fun txn(
        type: TxnType,
        amount: Long,
        accountId: Long,
        at: Instant = afterCutover,
        toAccountId: Long? = null,
        id: Long = nextId++,
    ) = Transaction(
        id = id,
        occurredAt = at,
        type = type,
        amount = Money(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        createdAt = at,
    )

    // Requirement: derive balances from an opening balance and an as-of date.

    @Test
    fun `historical rows do not move balances`() {
        val bca = account("BCA", opening = 8_420_000)
        // Stand-in for the 1,150 imported rows, every one of them before the cutover.
        val history = (1..20).map {
            txn(TxnType.EXPENSE, 100_000, bca.id, at = cutover - it.hours)
        }

        assertEquals(Money(8_420_000), deriveBalance(bca, history))
    }

    @Test
    fun `a transaction on the as-of date counts`() {
        val bca = account("BCA", opening = 1_000_000)

        // Exactly on the cutover instant: the clause is >=, not >.
        val onTheDay = listOf(txn(TxnType.EXPENSE, 187_000, bca.id, at = cutover))
        val balance = deriveBalance(bca, onTheDay)

        assertEquals(Money(813_000), balance)
    }

    @Test
    fun `a transfer moves both balances and leaves the total unchanged`() {
        val bca = account("BCA", opening = 8_420_000)
        val rdpu = account("RDPU SucorInvest", AccountType.INVESTMENT, opening = 5_000_000)
        val transfer = listOf(txn(TxnType.TRANSFER, 500_000, bca.id, toAccountId = rdpu.id))

        val fromBalance = deriveBalance(bca, transfer)
        val toBalance = deriveBalance(rdpu, transfer)

        assertEquals(Money(7_920_000), fromBalance)
        assertEquals(Money(5_500_000), toBalance)
        // The fix for the investment black hole: one row, two legs, nothing lost.
        assertEquals(Money(13_420_000), fromBalance + toBalance)
    }

    @Test
    fun `a transfer dated before the destination cutover credits neither leg twice`() {
        val bca = account("BCA", opening = 1_000_000, asOf = cutover - 30.days)
        val rdpu = account("RDPU", AccountType.INVESTMENT, opening = 5_000_000, asOf = cutover)
        val early = listOf(
            txn(TxnType.TRANSFER, 500_000, bca.id, at = cutover - 10.days, toAccountId = rdpu.id),
        )

        assertEquals(Money(500_000), deriveBalance(bca, early))
        assertEquals(Money(5_000_000), deriveBalance(rdpu, early))
    }

    @Test
    fun `an adjustment carries its own sign`() {
        val cash = account("Cash", AccountType.CASH, opening = 1_038_500)

        val down = deriveBalance(cash, listOf(txn(TxnType.ADJUSTMENT, -53_500, cash.id)))
        val up = deriveBalance(cash, listOf(txn(TxnType.ADJUSTMENT, 25_000, cash.id)))

        assertEquals(Money(985_000), down)
        assertEquals(Money(1_063_500), up)
    }

    @Test
    fun `the ledger runs newest first and ends on the derived balance`() {
        val bca = account("BCA", opening = 1_000_000)
        val rows = listOf(
            txn(TxnType.EXPENSE, 187_000, bca.id, at = afterCutover),
            txn(TxnType.INCOME, 500_000, bca.id, at = afterCutover + 1.hours),
            // Not this account's row at all.
            txn(TxnType.EXPENSE, 999_000, accountId = 99),
            // Before the cutover, so it moves nothing and is left off the ledger.
            txn(TxnType.EXPENSE, 40_000, bca.id, at = cutover - 1.hours),
        )

        val ledger = accountLedger(bca, rows)

        assertEquals(2, ledger.size)
        assertEquals(Money(500_000), ledger.first().effect)
        assertEquals(Money(1_313_000), ledger.first().runningBalance)
        assertEquals(Money(-187_000), ledger.last().effect)
        assertEquals(Money(813_000), ledger.last().runningBalance)
        // The column has to reconcile against the header, or it explains nothing.
        assertEquals(deriveBalance(bca, rows), ledger.first().runningBalance)
    }

    @Test
    fun `a transfer reads as an outflow on one side and an inflow on the other`() {
        val bca = account("BCA", opening = 1_000_000)
        val rdpu = account("RDPU", AccountType.INVESTMENT, opening = 0)
        val transfer = txn(TxnType.TRANSFER, 500_000, bca.id, toAccountId = rdpu.id)

        assertEquals(Money(-500_000), transfer.effectOn(bca.id))
        assertEquals(Money(500_000), transfer.effectOn(rdpu.id))
        assertEquals(Money.ZERO, transfer.effectOn(accountId = 404))
    }

    // Requirement: summarise holdings as Spendable, Invested and Total.

    @Test
    fun `summary with no liabilities`() {
        val summary = accountSummary(
            listOf(
                account("BCA", balance = 8_420_000),
                account("Jago", balance = 2_150_000),
                account("Cash", AccountType.CASH, balance = 385_000),
                account("RDPU", AccountType.INVESTMENT, balance = 5_000_000),
            ),
        )

        assertEquals(Money(10_955_000), summary.spendable)
        assertEquals(Money(5_000_000), summary.invested)
        assertEquals(Money(15_955_000), summary.total)
        // Null, not zero: the figure is not rendered at all until a liability exists.
        assertNull(summary.owed)
    }

    @Test
    fun `taking on debt lowers net worth`() {
        // The card's stored balance is negative because spending on it is an expense.
        val summary = accountSummary(
            listOf(
                account("BCA", balance = 1_000_000),
                account("Kartu", AccountType.LIABILITY, balance = -500_000),
            ),
        )

        assertEquals(Money(1_000_000), summary.spendable)
        // Presented positive — the amount owed — and subtracted from the total.
        assertEquals(Money(500_000), summary.owed)
        assertEquals(Money(500_000), summary.total)
    }

    @Test
    fun `a liabilities figure appears as soon as a liability account does`() {
        val without = accountSummary(listOf(account("BCA", balance = 1_000_000)))
        val with = accountSummary(
            listOf(
                account("BCA", balance = 1_000_000),
                account("Kartu", AccountType.LIABILITY, balance = -250_000),
            ),
        )

        assertNull(without.owed)
        assertEquals(Money(250_000), with.owed)
        assertEquals(without.total - Money(250_000), with.total)
    }

    @Test
    fun `an archived account stops counting towards every figure`() {
        val accounts = listOf(
            account("BCA", balance = 1_000_000),
            account("Gopay", balance = 500_000, archived = true),
            account("RDPU", AccountType.INVESTMENT, balance = 2_000_000, archived = true),
        )

        val summary = accountSummary(accounts)

        assertEquals(Money(1_000_000), summary.spendable)
        assertEquals(Money.ZERO, summary.invested)
        assertEquals(Money(1_000_000), summary.total)
    }

    // Requirement: chart balances by account.

    @Test
    fun `four positive balances make four segments summing to one hundred percent`() {
        val mix = accountMix(
            listOf(
                account("BCA", balance = 8_420_000),
                account("Jago", balance = 2_150_000),
                account("Cash", AccountType.CASH, balance = 385_000),
                account("RDPU", AccountType.INVESTMENT, balance = 5_000_000),
            ),
        )

        assertEquals(4, mix.size)
        assertEquals(100, mix.sumOf { it.percent })
        // Largest first, so the ring reads from twelve o'clock down.
        assertEquals(listOf(1L, 4L, 2L, 3L), mix.map { it.accountId })
    }

    @Test
    fun `six equal balances still sum to one hundred rather than to one hundred and two`() {
        val equal = (1..6).map { account("Account $it", balance = 1_000_000) }

        val mix = accountMix(equal)

        assertEquals(100, mix.sumOf { it.percent })
    }

    @Test
    fun `a zero or negative balance is left off the chart`() {
        val accounts = listOf(
            account("BCA", balance = 1_000_000),
            account("Empty", AccountType.CASH, balance = 0),
            account("Kartu", AccountType.LIABILITY, balance = -2_000_000),
        )

        val mix = accountMix(accounts)

        // A donut cannot represent a negative share, and a debt's magnitude would
        // otherwise draw the largest slice on the chart.
        assertEquals(listOf(1L), mix.map { it.accountId })
        assertEquals(100, mix.single().percent)

        // Still listed, all three of them, with their own figures.
        val groups = accountGroups(accounts, mix, now, zone)
        assertEquals(3, groups.sumOf { it.rows.size })
        assertEquals(
            listOf(Money(1_000_000), Money.ZERO, Money(-2_000_000)),
            groups.flatMap { it.rows }.sortedBy { it.id }.map { it.balance },
        )
        assertNull(groups.flatMap { it.rows }.first { it.name == "Kartu" }.sharePercent)
    }

    @Test
    fun `an archived account is off the chart even while it holds money`() {
        val mix = accountMix(
            listOf(
                account("BCA", balance = 1_000_000),
                account("Gopay", balance = 500_000, archived = true),
            ),
        )

        assertEquals(listOf(1L), mix.map { it.accountId })
    }

    @Test
    fun `nothing to chart yields no segments rather than a divide by zero`() {
        assertTrue(accountMix(emptyList()).isEmpty())
        assertTrue(accountMix(listOf(account("Empty", balance = 0))).isEmpty())
    }

    // Requirement: flag accounts that have drifted out of check.

    @Test
    fun `an account reconciled forty-five days ago is stale and names the date`() {
        val account = account("Cash", AccountType.CASH, lastReconciled = now - 45.days)

        val state = reconciliationOf(account, now, zone)

        assertEquals(Reconciliation.Stale(LocalDate(2026, 7, 6)), state)
    }

    @Test
    fun `an account never reconciled is its own state`() {
        val state = reconciliationOf(account("Cash", lastReconciled = null), now, zone)

        assertEquals(Reconciliation.Never, state)
    }

    @Test
    fun `an account checked inside the window is current`() {
        val account = account("BCA", lastReconciled = now - (STALE_AFTER - 1.days))

        assertTrue(reconciliationOf(account, now, zone) is Reconciliation.Current)
    }

    // Requirement: reconcile an account against its real balance.

    @Test
    fun `reconciling downward is a negative adjustment`() {
        assertEquals(Money(-53_500), adjustmentFor(Money(1_038_500), Money(985_000)))
    }

    @Test
    fun `reconciling to the same figure adjusts nothing`() {
        assertTrue(adjustmentFor(Money(985_000), Money(985_000)).isZero)
    }

    // Grouping.

    @Test
    fun `accounts group by type in the order the screen lists them, empty groups dropped`() {
        val accounts = listOf(
            account("BCA", AccountType.BANK, balance = 1_000_000),
            account("Cash", AccountType.CASH, balance = 100_000),
            account("Jago", AccountType.BANK, balance = 200_000),
        )

        val groups = accountGroups(accounts, accountMix(accounts), now, zone)

        assertEquals(listOf(AccountType.CASH, AccountType.BANK), groups.map { it.type })
        assertEquals(listOf("BCA", "Jago"), groups[1].rows.map { it.name })
    }

    @Test
    fun `an archived account is kept out of the groups so it can be listed apart`() {
        val accounts = listOf(
            account("BCA", balance = 1_000_000),
            account("Gopay", balance = 0, archived = true),
        )

        val groups = accountGroups(accounts, accountMix(accounts), now, zone)

        assertEquals(listOf("BCA"), groups.flatMap { it.rows }.map { it.name })
    }

    @Test
    fun `a row carries the opening figures the editor writes back`() {
        val accounts = listOf(account("BCA", opening = 8_420_000, balance = 8_000_000))

        val row = accountGroups(accounts, emptyList(), now, zone).single().rows.single()

        // Read off the row rather than defaulted when the editor opens: a form that
        // defaulted these would reset a real opening balance to zero on a rename.
        assertEquals(Money(8_420_000), row.openingBalance)
        assertEquals(LocalDate(2026, 8, 17), row.openingAsOf)
    }
}
