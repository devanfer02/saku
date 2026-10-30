package com.dvnnfrr.saku.data.repository

import androidx.room.withTransaction
import com.dvnnfrr.saku.data.local.SakuDatabase
import com.dvnnfrr.saku.data.local.TxnEntity
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.DedupHash
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.NoteUsage
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.domain.truncatedToSeconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime

class RoomTransactionRepository(
    private val db: SakuDatabase,
    private val clock: Clock = Clock.System,
) : TransactionRepository {

    private val txnDao = db.txnDao()
    private val categoryDao = db.categoryDao()
    private val subcategoryDao = db.subcategoryDao()
    private val accountDao = db.accountDao()

    override fun observeInRange(
        range: DateRange,
        zone: TimeZone,
        filter: TransactionFilter,
    ): Flow<List<Transaction>> {
        val f = filter.toArgs()
        return txnDao.observeInRange(
            range.startSec(zone), range.endSec(zone),
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeForCategory(
        range: DateRange,
        zone: TimeZone,
        categoryId: Long,
        type: TxnType,
        filter: TransactionFilter,
    ): Flow<List<Transaction>> {
        val f = filter.toArgs()
        return txnDao.observeInRangeForCategory(
            range.startSec(zone), range.endSec(zone), categoryId, type.name,
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeForCategories(
        range: DateRange,
        zone: TimeZone,
        categoryIds: List<Long>,
        type: TxnType,
        filter: TransactionFilter,
    ): Flow<List<Transaction>> {
        val f = filter.toArgs()
        return txnDao.observeInRangeForCategories(
            range.startSec(zone), range.endSec(zone), categoryIds, type.name,
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observePeriodTotals(
        range: DateRange,
        zone: TimeZone,
        filter: TransactionFilter,
    ): Flow<PeriodTotals> {
        val f = filter.toArgs()
        return txnDao.observePeriodTotals(
            range.startSec(zone), range.endSec(zone),
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { PeriodTotals(Money(it.income), Money(it.expense)) }
    }

    override fun observeDailyTotals(
        range: DateRange,
        zone: TimeZone,
        filter: TransactionFilter,
    ): Flow<List<DailyTotals>> {
        // One offset for the whole range. Correct for WIB, which has no DST; a zone
        // that does would bucket the hour after a transition a day off.
        val offset = zone.offsetAt(range.startInstant(zone)).totalSeconds
        val f = filter.toArgs()
        return txnDao.observeDailyTotals(
            range.startSec(zone), range.endSec(zone), offset,
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { rows ->
            rows.map { row ->
                DailyTotals(
                    date = Instant.fromEpochSeconds(row.dayEpochSec).toLocalDateTime(zone).date,
                    income = Money(row.income),
                    expense = Money(row.expense),
                )
            }
        }
    }

    override fun observeCategoryTotals(
        range: DateRange,
        zone: TimeZone,
        type: TxnType,
        filter: TransactionFilter,
    ): Flow<List<CategoryTotal>> {
        val f = filter.toArgs()
        return txnDao.observeCategoryTotals(
            range.startSec(zone), range.endSec(zone), type.name,
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { it.toDomain() }
    }

    override fun observeAccountFlows(range: DateRange, zone: TimeZone): Flow<List<AccountFlows>> =
        txnDao.observeAccountFlows(range.startSec(zone), range.endSec(zone))
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(filter: TransactionFilter): Flow<List<Transaction>> {
        val f = filter.toArgs()
        return txnDao.observeAll(
            f.allAccounts, f.accountIds, f.allIncome, f.incomeIds, f.allExpense, f.expenseIds,
        ).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeNoteUsage(type: TxnType): Flow<List<NoteUsage>> =
        txnDao.observeNoteUses(type.name).map(::foldNoteUsage)

    override suspend fun byId(id: Long): Transaction? = txnDao.byId(id)?.toDomain()

    override suspend fun all(): List<Transaction> = txnDao.all().map { it.toDomain() }

    override suspend fun lastEnteredAt(): Instant? =
        txnDao.lastEnteredAtEpochSec()?.let(Instant::fromEpochSeconds)

    override suspend fun record(
        occurredAt: Instant,
        type: TxnType,
        amount: Money,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        subcategoryId: Long?,
        note: String?,
    ): Long = db.withTransaction {
        validate(occurredAt, type, amount, accountId, toAccountId, categoryId, subcategoryId)

        val at = occurredAt.truncatedToSeconds()
        txnDao.insert(
            TxnEntity(
                occurredAtEpochSec = at.epochSeconds,
                type = type,
                amountIdr = amount.rupiah,
                accountId = accountId,
                toAccountId = toAccountId,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                note = note?.takeIf { it.isNotBlank() },
                createdAtEpochSec = clock.now().epochSeconds,
                dedupHash = DedupHash.of(
                    at, type, amount, accountId, toAccountId,
                    categoryId?.let { categoryDao.byId(it)?.name }, note,
                ),
            ),
        )
    }

    override suspend fun update(transaction: Transaction) {
        db.withTransaction {
            val existing = txnDao.byId(transaction.id) ?: return@withTransaction
            validate(
                transaction.occurredAt,
                transaction.type,
                transaction.amount,
                transaction.accountId,
                transaction.toAccountId,
                transaction.categoryId,
                transaction.subcategoryId,
            )

            val at = transaction.occurredAt.truncatedToSeconds()
            txnDao.update(
                existing.copy(
                    occurredAtEpochSec = at.epochSeconds,
                    type = transaction.type,
                    amountIdr = transaction.amount.rupiah,
                    accountId = transaction.accountId,
                    toAccountId = transaction.toAccountId,
                    categoryId = transaction.categoryId,
                    subcategoryId = transaction.subcategoryId,
                    note = transaction.note?.takeIf { it.isNotBlank() },
                    dedupHash = DedupHash.of(
                        at,
                        transaction.type,
                        transaction.amount,
                        transaction.accountId,
                        transaction.toAccountId,
                        transaction.categoryId?.let { categoryDao.byId(it)?.name },
                        transaction.note,
                    ),
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        txnDao.byId(id)?.let { txnDao.delete(it) }
    }

    /**
     * One rule set for both write paths.
     *
     * `update` previously enforced none of this, so an edit could write states an entry
     * could not: a zero amount, a self-transfer, or an expense with no category. The
     * last one is visibly wrong on screen, because `observeCategoryTotals` inner-joins
     * `category` while `observePeriodTotals` does not — the donut stopped summing to
     * the header above it.
     */
    internal suspend fun validate(
        occurredAt: Instant,
        type: TxnType,
        amount: Money,
        accountId: Long,
        toAccountId: Long?,
        categoryId: Long?,
        subcategoryId: Long?,
    ) {
        // Only an ADJUSTMENT may be signed — reconcile writes a downward correction as
        // a negative addend. For the other three, `amountIdr` is a magnitude the
        // balance query negates by type, so a negative expense ADDS to the balance:
        // spending would raise it, and its category's share would go negative and push
        // another slice of the donut past 100%. A keypad cannot produce one, but an
        // imported sheet using the signed-amount convention can.
        if (type == TxnType.ADJUSTMENT) {
            require(!amount.isZero) { "An adjustment must be non-zero" }
        } else {
            require(amount.isPositive) {
                "$type needs a positive amount; direction comes from the type, not the sign"
            }
        }

        if (type.requiresCategory) {
            requireNotNull(categoryId) { "$type requires a category" }
        }

        if (subcategoryId != null) {
            val parent = subcategoryDao.byId(subcategoryId)?.categoryId
            require(parent == categoryId) {
                "Subcategory $subcategoryId belongs to category $parent, not $categoryId"
            }
        }

        if (type.requiresDestination) {
            requireNotNull(toAccountId) { "$type requires a destination account" }
            require(toAccountId != accountId) { "A transfer needs two different accounts" }

            // A transfer dated before either account's as-of date is counted on one
            // leg and not the other, so it invents or destroys money in the Total.
            // The opening balances already account for anything that early.
            val from = accountDao.byId(accountId)
            val to = accountDao.byId(toAccountId)
            val at = occurredAt.truncatedToSeconds().epochSeconds
            require(from == null || at >= from.openingAsOfEpochSec) {
                "Transfer predates ${from?.name}'s opening date; it would not be debited"
            }
            require(to == null || at >= to.openingAsOfEpochSec) {
                "Transfer predates ${to?.name}'s opening date; it would not be credited"
            }
        }
    }
}

internal fun DateRange.startSec(zone: TimeZone): Long = startInstant(zone).epochSeconds

internal fun DateRange.endSec(zone: TimeZone): Long = endInstantExclusive(zone).epochSeconds
