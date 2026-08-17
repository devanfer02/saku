package com.dvnnfrr.saku.ui.accounts

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountGroup
import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.wholePercents
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the accounts screen. No Compose, no Android, no coroutines — the
 * balance formula, the Spendable/Invested/Total fold, the donut's shares and the
 * staleness rule are all decided here and tested on the JVM against plain lists.
 *
 * The balance arithmetic exists twice in this app: once in SQL, in
 * `AccountDao.observeBalances`, and once here. That is deliberate and it is the reason
 * for `AccountsViewModelTest.the Kotlin fold and the SQL aggregate agree`. The account
 * detail has to show what each row did to the balance and what the balance was after
 * it, which an aggregate cannot answer — but a second implementation of a money rule
 * drifts unless something holds the two together, so the test asserts the running total
 * lands exactly on the figure the DAO reports.
 */

/**
 * What one transaction does to one account's balance, signed.
 *
 * A transfer is a single row naming both accounts, so it is read twice here — once as
 * the source's outflow and once as the destination's inflow — mirroring the two
 * subqueries the DAO sums. Reading it once and negating for the far side is what let an
 * earlier design lose a leg and invent money in the total.
 *
 * An ADJUSTMENT already carries its sign: a downward correction is a negative addend.
 */
fun Transaction.effectOn(accountId: Long): Money {
    var effect = Money.ZERO
    if (this.accountId == accountId) {
        effect += when (type) {
            TxnType.INCOME, TxnType.ADJUSTMENT -> amount
            TxnType.EXPENSE, TxnType.TRANSFER -> -amount
        }
    }
    if (type == TxnType.TRANSFER && toAccountId == accountId) effect += amount
    return effect
}

/** True when the row touches [accountId] at all, on either leg. */
fun Transaction.touches(accountId: Long): Boolean =
    this.accountId == accountId || toAccountId == accountId

/**
 * Whether the row is on or after the account's cutover, which is the whole of the date
 * clause: `occurredAt >= openingAsOf`, inclusive. A year of imported history
 * sits before it and moves no balance.
 */
fun Transaction.countsToward(account: Account): Boolean = occurredAt >= account.openingAsOf

/**
 * `openingBalance + inflows − outflows`, over the rows dated on or after the cutover.
 *
 * The Kotlin half of the pair described at the top of this file.
 */
fun deriveBalance(account: Account, transactions: List<Transaction>): Money =
    transactions.fold(account.openingBalance) { running, transaction ->
        if (transaction.countsToward(account)) {
            running + transaction.effectOn(account.id)
        } else {
            running
        }
    }

/** One row of the account detail: what happened, what it did, and where it left the balance. */
data class LedgerEntry(
    val transaction: Transaction,
    /** Signed, from this account's point of view. */
    val effect: Money,
    /** The account's balance immediately after this row. */
    val runningBalance: Money,
)

/**
 * The account's rows folded into a running balance, newest first.
 *
 * Rows dated before the cutover are left out. They are real and they are in the
 * transaction list, but their effect on this balance is exactly zero, and 1,150 of them
 * carrying "no change" would bury the handful that actually moved the figure. What is
 * left ends on the derived balance, so the column reconciles against the header.
 *
 * Ordered oldest first to fold, then reversed: a running balance only means anything
 * accumulated forwards, while a ledger only reads sensibly backwards.
 */
fun accountLedger(account: Account, transactions: List<Transaction>): List<LedgerEntry> {
    var running = account.openingBalance
    return transactions
        .asSequence()
        .filter { it.touches(account.id) && it.countsToward(account) }
        .sortedWith(compareBy<Transaction> { it.occurredAt }.thenBy { it.id })
        .map { transaction ->
            val effect = transaction.effectOn(account.id)
            running += effect
            LedgerEntry(transaction = transaction, effect = effect, runningBalance = running)
        }
        .toList()
        .asReversed()
}

/**
 * Spendable, Invested and the amount owed, folded from the same list the screen renders.
 *
 * Deliberately not read from `AccountRepository.observeSummary`, even though that exists:
 * a header sourced from a different query than the list under it can disagree with it,
 * and a net worth that does not equal the sum of the rows beneath is the one number in
 * this app nobody would forgive. The repository's version is still asserted against this
 * one in the ViewModel test.
 *
 * Archived accounts are excluded — archiving is what takes an account out of the figures
 * while leaving its history alone.
 *
 * A liability's stored balance is NEGATIVE while money is owed, so [AccountSummary.owed]
 * negates the sum to show a positive amount owed, and [AccountSummary.total] subtracts
 * it. Folding the raw negative in as an asset made net worth rise when debt was taken on.
 */
fun accountSummary(accounts: List<Account>): AccountSummary {
    val byGroup = accounts.filterNot { it.archived }.groupBy { it.group }
    val liabilities = byGroup[AccountGroup.OWED].orEmpty()
    return AccountSummary(
        spendable = byGroup[AccountGroup.SPENDABLE].orEmpty().sumBalances(),
        invested = byGroup[AccountGroup.INVESTED].orEmpty().sumBalances(),
        // Null rather than zero, so the Liabilities figure stays hidden until a
        // liability account exists rather than sitting at zero for ever.
        owed = liabilities.takeIf { it.isNotEmpty() }?.let { -it.sumBalances() },
    )
}

private fun List<Account>.sumBalances(): Money =
    fold(Money.ZERO) { total, account -> total + account.balance }

/** One donut segment and its legend row. */
data class AccountShare(
    val accountId: Long,
    val percent: Int,
)

private const val BASIS_POINTS = 10_000L

/**
 * Share of holdings per account, largest first, summing to exactly 100%.
 *
 * Only positive balances are charted. A zero share is not a slice, a negative one would
 * eat its neighbours', and a liability owed money is negative by construction — so a
 * card with 2.000.000 outstanding contributes nothing to the ring rather than a slice
 * the size of the debt. Every account is still listed with its figure; the list is what
 * carries identity, and it is why the chart is allowed to leave accounts out.
 *
 * Percentages come from [wholePercents] rather than from rounding each share on its own,
 * so the legend adds up to 100 instead of to 102.
 */
fun accountMix(accounts: List<Account>): List<AccountShare> {
    val drawn = accounts
        .filterNot { it.archived }
        .filter { it.balance.isPositive }
        .sortedWith(compareByDescending<Account> { it.balance }.thenBy { it.sortOrder })
    val total = drawn.fold(0L) { sum, account -> sum + account.balance.rupiah }
    if (total <= 0L) return emptyList()

    // CategoryTotal is the shape wholePercents takes. It is the app's "one slice plus its
    // ranked row" type rather than anything category-specific, and reusing it is what
    // keeps one largest-remainder implementation in the codebase instead of two.
    val totals = drawn.map { account ->
        CategoryTotal(
            categoryId = account.id,
            name = account.name,
            colorSlot = account.colorSlot,
            total = account.balance,
            shareBasisPoints = (account.balance.rupiah * BASIS_POINTS / total).toInt(),
        )
    }
    val percents = wholePercents(totals)
    return totals.mapIndexed { index, slice ->
        AccountShare(accountId = slice.categoryId, percent = percents[index])
    }
}

/** How long ago the account was last checked against reality. */
sealed interface Reconciliation {
    /** Checked inside the window. Carries the date anyway, because the row shows it. */
    data class Current(val lastReconciled: LocalDate) : Reconciliation

    /** Past the window. The row names the date so the user can see how far past. */
    data class Stale(val lastReconciled: LocalDate) : Reconciliation

    /** Never checked at all, which is a distinct state and reads in the alert colour. */
    data object Never : Reconciliation
}

/**
 * Balances drift because half of all entries get logged from memory days later, so an
 * account that has not been checked in a month is flagged rather than trusted.
 */
fun reconciliationOf(account: Account, now: Instant, zone: TimeZone): Reconciliation {
    val last = account.lastReconciled ?: return Reconciliation.Never
    val date = last.toLocalDateTime(zone).date
    return if (account.isStale(now)) {
        Reconciliation.Stale(date)
    } else {
        Reconciliation.Current(date)
    }
}

/**
 * What reconciling to [trueBalance] would write. Zero means the balance already matched
 * and no adjustment is created — the reconciliation date is still stamped, because
 * "checked, and it was right" is exactly the fact the staleness flag is asking about.
 */
fun adjustmentFor(derived: Money, trueBalance: Money): Money = trueBalance - derived

/** Accounts of one type, in the order the screen lists them. */
data class AccountGroupUi(
    val type: AccountType,
    val rows: List<AccountRowUi>,
)

/** One account as the list renders it. */
data class AccountRowUi(
    val id: Long,
    val name: String,
    val type: AccountType,
    val colorSlot: ColorSlot,
    val balance: Money,
    val archived: Boolean,
    val reconciliation: Reconciliation,
    /** Null when the account holds no positive balance and is therefore off the chart. */
    val sharePercent: Int?,
    /*
     * Carried on every row, not fetched when the editor opens. The editor writes all
     * four fields at once, so a form that defaulted these would quietly reset a real
     * opening balance to zero the first time someone corrected a typo in a name.
     */
    val openingBalance: Money,
    val openingAsOf: LocalDate,
)

/**
 * Groups the live accounts by type, in the enum's own order — Cash, Bank, Investment,
 * Liability — and drops the groups that hold nothing, so a user with no liabilities
 * never sees an empty Liability heading.
 */
fun accountGroups(
    accounts: List<Account>,
    shares: List<AccountShare>,
    now: Instant,
    zone: TimeZone,
): List<AccountGroupUi> {
    val byId = shares.associate { it.accountId to it.percent }
    val grouped = accounts.filterNot { it.archived }.groupBy { it.type }
    return AccountType.entries.mapNotNull { type ->
        val group = grouped[type] ?: return@mapNotNull null
        AccountGroupUi(type = type, rows = group.map { it.toRowUi(byId[it.id], now, zone) })
    }
}

fun Account.toRowUi(sharePercent: Int?, now: Instant, zone: TimeZone): AccountRowUi = AccountRowUi(
    id = id,
    name = name,
    type = type,
    colorSlot = colorSlot,
    balance = balance,
    archived = archived,
    reconciliation = reconciliationOf(this, now, zone),
    sharePercent = sharePercent,
    openingBalance = openingBalance,
    openingAsOf = openingAsOf.toLocalDateTime(zone).date,
)
