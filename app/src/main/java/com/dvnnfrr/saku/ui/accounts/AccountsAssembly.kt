package com.dvnnfrr.saku.ui.accounts

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountGroup
import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PERMILLE_WHOLE
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.barPermille
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the accounts screen. No Compose, no Android, no coroutines — the
 * balance formula, the Spendable/Invested/Total fold, the net worth split and the
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

/**
 * Net worth and the one bar that shows what it is made of.
 *
 * The bar splits Spendable against Invested, the two kinds of holding; the amount owed
 * is a third labelled figure beside it rather than a third segment, because a debt is
 * not a share of what is held. [spendablePermille] and [investedPermille] are thousandths
 * of the bar and sum to exactly 1000 whenever anything is drawn.
 *
 * A group below zero draws no segment. Cash pushed negative by imported history would
 * otherwise eat the Invested segment, and the warning on its row already says why.
 */
data class NetWorthSplit(
    val total: Money,
    val spendable: Money,
    val invested: Money,
    val owed: Money?,
    val spendablePermille: Int,
    val investedPermille: Int,
) {
    /** False while neither group holds money, so the screen asks for an opening balance. */
    val hasBar: Boolean get() = spendablePermille + investedPermille > 0
}

fun netWorthSplit(summary: AccountSummary): NetWorthSplit {
    val spendable = summary.spendable.takeIf { it.isPositive } ?: Money.ZERO
    val invested = summary.invested.takeIf { it.isPositive } ?: Money.ZERO
    val whole = spendable + invested
    val spendableShare = barPermille(spendable, whole)
    return NetWorthSplit(
        total = summary.total,
        spendable = summary.spendable,
        invested = summary.invested,
        owed = summary.owed,
        spendablePermille = spendableShare,
        // The remainder rather than a second rounding, so the two always fill the bar.
        investedPermille = if (whole.isPositive) PERMILLE_WHOLE - spendableShare else 0,
    )
}

/**
 * Below zero is a problem only where it cannot honestly happen. A liability is negative
 * by construction while money is owed, so a credit card in debt is not flagged; a cash
 * or bank account below zero almost always means history predates a missing opening
 * balance.
 */
fun Account.isBelowZero(): Boolean = balance.isNegative && type != AccountType.LIABILITY

/** How long ago the account was last checked against reality. */
sealed interface Reconciliation {
    /** Checked inside the window. Carries the date anyway, because the row shows it. */
    data class Current(val lastReconciled: LocalDate) : Reconciliation

    /** Past the window. The row names the date so the user can see how far past. */
    data class Stale(val lastReconciled: LocalDate) : Reconciliation

    /** Never checked at all. A distinct state, but neutral: imported accounts all start here. */
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
    /** Takes precedence over the check status: the row says why the figure is wrong. */
    val belowZero: Boolean,
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
    now: Instant,
    zone: TimeZone,
): List<AccountGroupUi> {
    val grouped = accounts.filterNot { it.archived }.groupBy { it.type }
    return AccountType.entries.mapNotNull { type ->
        val group = grouped[type] ?: return@mapNotNull null
        AccountGroupUi(type = type, rows = group.map { it.toRowUi(now, zone) })
    }
}

fun Account.toRowUi(now: Instant, zone: TimeZone): AccountRowUi = AccountRowUi(
    id = id,
    name = name,
    type = type,
    colorSlot = colorSlot,
    balance = balance,
    archived = archived,
    reconciliation = reconciliationOf(this, now, zone),
    belowZero = isBelowZero(),
    openingBalance = openingBalance,
    openingAsOf = openingAsOf.toLocalDateTime(zone).date,
)
