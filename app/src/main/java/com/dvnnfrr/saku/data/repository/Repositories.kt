package com.dvnnfrr.saku.data.repository

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.AccountSummary
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.CategoryTotal
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.excel.BackupContents
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/*
 * THE CONTRACTS.
 *
 * Phase 3 runs four tracks concurrently against these interfaces. They freeze at the
 * fan-out gate (task 8.11): a track needing a change here raises it rather than
 * editing, because this file is four tracks' common ground.
 */

interface TransactionRepository {
    /*
     * Every read that feeds Trans and Stats takes a required [TransactionFilter]. Not a
     * default argument: a screen that forgot it would compile and silently ignore the
     * filter, and the compiler listing every caller is the point. Pass
     * [TransactionFilter.ALL] where a screen is deliberately unfiltered.
     */

    fun observeInRange(
        range: DateRange,
        zone: TimeZone,
        filter: TransactionFilter,
    ): Flow<List<Transaction>>

    /**
     * Drill-through from a stats row. [type] is required, not optional: the row that
     * opens this list came from `observeCategoryTotals`, which filters by type, so
     * omitting it here lists transfers tagged with the category and the drill-through
     * total no longer matches the row the user tapped.
     */
    fun observeForCategory(
        range: DateRange,
        zone: TimeZone,
        categoryId: Long,
        type: TxnType,
        filter: TransactionFilter,
    ): Flow<List<Transaction>>

    /** Drill-through for the stats screen's aggregated "Other" slice. */
    fun observeForCategories(
        range: DateRange,
        zone: TimeZone,
        categoryIds: List<Long>,
        type: TxnType,
        filter: TransactionFilter,
    ): Flow<List<Transaction>>

    /** Income and expense for the period. Transfers and adjustments are excluded. */
    fun observePeriodTotals(
        range: DateRange,
        zone: TimeZone,
        filter: TransactionFilter,
    ): Flow<PeriodTotals>

    /** Per-day subtotals for the Daily headers, Calendar cells and Monthly rows. */
    fun observeDailyTotals(
        range: DateRange,
        zone: TimeZone,
        filter: TransactionFilter,
    ): Flow<List<DailyTotals>>

    /** Ranked category composition for one side of the stats screen. */
    fun observeCategoryTotals(
        range: DateRange,
        zone: TimeZone,
        type: TxnType,
        filter: TransactionFilter,
    ): Flow<List<CategoryTotal>>

    /**
     * Income, expense and both transfer legs per account for the period, adjustments
     * excluded. Unfiltered on purpose: the filter sheet lists every account so the user
     * can choose among them. An account nothing moved through has no entry.
     */
    fun observeAccountFlows(range: DateRange, zone: TimeZone): Flow<List<AccountFlows>>

    /** Every transaction passing [filter], newest first, across all history. For search. */
    fun observeAll(filter: TransactionFilter): Flow<List<Transaction>>

    suspend fun byId(id: Long): Transaction?

    /** Every transaction, oldest first. For export, which cannot be range-scoped. */
    suspend fun all(): List<Transaction>

    /** Pre-fill for the entry sheet, so subcategory stays optional without staying empty. */
    suspend fun lastSubcategoryFor(categoryId: Long): Long?

    suspend fun record(
        occurredAt: Instant,
        type: TxnType,
        amount: Money,
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        subcategoryId: Long? = null,
        note: String? = null,
    ): Long

    /** Validated identically to [record]; an edit cannot write a state an entry could not. */
    suspend fun update(transaction: Transaction)

    suspend fun delete(id: Long)
}

interface AccountRepository {
    /** Includes archived accounts, so the manage screen can offer unarchive. */
    fun observeAll(): Flow<List<Account>>

    fun observeActive(): Flow<List<Account>>

    fun observeSummary(): Flow<AccountSummary>

    suspend fun byId(id: Long): Account?

    /** Every account with its balance, as a one-shot read. For export and import mapping. */
    suspend fun all(): List<Account>

    /**
     * Finds by name or creates, which is how import maps a file's account names.
     * Matches archived accounts too, unarchiving rather than creating a same-named
     * duplicate — two rows with one name split the history between them.
     */
    suspend fun findOrCreate(name: String, type: AccountType, openingAsOf: Instant): Long

    suspend fun create(
        name: String,
        type: AccountType,
        openingBalance: Money,
        openingAsOf: Instant,
        colorSlot: ColorSlot? = null,
    ): Long

    suspend fun rename(id: Long, name: String)

    suspend fun retype(id: Long, type: AccountType)

    suspend fun recolor(id: Long, slot: ColorSlot)

    /**
     * Sets the opening balance and its as-of date. First-run setup seeds accounts at
     * zero and then collects the real figures, so this is the only way that flow can
     * complete.
     */
    suspend fun setOpening(id: Long, openingBalance: Money, openingAsOf: Instant)

    suspend fun setArchived(id: Long, archived: Boolean)

    /** True when the account has no transactions and may therefore be hard-deleted. */
    suspend fun isDeletable(id: Long): Boolean

    suspend fun delete(id: Long)

    /**
     * Records the account's true balance. Where it differs from the derived balance an
     * ADJUSTMENT transaction is written for the difference; the balance is never
     * silently overwritten. Returns the adjustment's id, or null when nothing differed.
     */
    suspend fun reconcile(id: Long, trueBalance: Money, at: Instant): Long?
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>

    fun observeActiveByKind(kind: CategoryKind): Flow<List<Category>>

    fun observeSubcategories(categoryId: Long): Flow<List<Subcategory>>

    /** Includes archived, so the manage screen can offer unarchive. */
    fun observeAllSubcategories(categoryId: Long): Flow<List<Subcategory>>

    /**
     * Every subcategory across every category, archived included.
     *
     * The transaction list needs a name for whatever subcategory a row points at, and
     * asking per category is an N+1. Archived rows stay in: archiving does not rewrite
     * the transactions already referencing the subcategory, so excluding them blanks
     * the label on real history.
     */
    fun observeAllSubcategories(): Flow<List<Subcategory>>

    suspend fun byId(id: Long): Category?

    suspend fun subcategoryById(id: Long): Subcategory?

    /** Throws [DuplicateNameException] on a case- and whitespace-insensitive clash. */
    suspend fun createCategory(name: String, kind: CategoryKind): Long

    suspend fun createSubcategory(categoryId: Long, name: String): Long

    suspend fun renameCategory(id: Long, name: String)

    suspend fun renameSubcategory(id: Long, name: String)

    suspend fun recolorCategory(id: Long, slot: ColorSlot)

    suspend fun reorderCategories(orderedIds: List<Long>)

    suspend fun reorderSubcategories(categoryId: Long, orderedIds: List<Long>)

    suspend fun setCategoryArchived(id: Long, archived: Boolean)

    suspend fun setSubcategoryArchived(id: Long, archived: Boolean)

    suspend fun isCategoryDeletable(id: Long): Boolean

    suspend fun isSubcategoryDeletable(id: Long): Boolean

    suspend fun deleteCategory(id: Long)

    suspend fun deleteSubcategory(id: Long)

    /** How many transactions a merge would move. Shown before the merge runs. */
    suspend fun transactionCount(categoryId: Long): Int

    /**
     * Transaction counts for every category and every subcategory in one query each.
     *
     * The category screen shows a count on every row and disables delete on any row
     * that has one. Asking per row is nineteen-plus suspend calls to paint one list —
     * the same N+1 the proving slice already found for subcategory names.
     */
    suspend fun transactionCountsByCategory(): Map<Long, Int>

    suspend fun transactionCountsBySubcategory(): Map<Long, Int>

    /** One-shot reads for export, which cannot be range-scoped or Flow-driven. */
    suspend fun allCategories(): List<Category>

    suspend fun allSubcategories(): List<Subcategory>

    /**
     * Moves every transaction from [fromId] to [toId], carries subcategories across
     * merging any name clash, then archives the source. Both must be the same kind.
     */
    suspend fun mergeCategory(fromId: Long, toId: Long): Int

    /**
     * Finds or creates by name — the path the importer uses. Matches archived
     * categories too, unarchiving rather than creating a same-named duplicate.
     */
    suspend fun findOrCreate(name: String, kind: CategoryKind): Long

    /** The subcategory equivalent, for round-tripping a Saku export. */
    suspend fun findOrCreateSubcategory(categoryId: Long, name: String): Long
}

interface ImportRepository {
    fun observeBatches(): Flow<List<ImportBatch>>

    /** Which of these hashes are already stored, so the preview can report duplicates. */
    suspend fun existingHashes(hashes: List<String>): Set<String>

    /**
     * Writes [rows] in one transaction, inserting only.
     *
     * [fileRowCount] is the number of rows in the file, which is not `rows.size` —
     * non-IDR rows and ones the user excluded at the preview are already gone by here.
     * Recording the file's real count keeps import history consistent with what the
     * preview showed.
     */
    suspend fun commit(
        fileName: String,
        rows: List<PendingRow>,
        fileRowCount: Int,
        at: Instant,
    ): ImportBatch

    /** Removes only the transactions this batch inserted; the batch record survives. */
    suspend fun reverse(batchId: Long, at: Instant): Int

    /**
     * Every account, category, subcategory and transaction, archived included, read in
     * one transaction so a transaction can never name an account the snapshot lacks.
     */
    suspend fun backupSnapshot(): BackupSnapshot

    /**
     * Inserts what [backup] has and this database lacks, in one transaction: missing
     * accounts, categories and subcategories first, then the transactions as one import
     * batch through [commit]. Matches by name and never updates an existing record, so a
     * same-named account keeps its own settings. Any failure leaves nothing behind.
     */
    suspend fun restore(backup: BackupContents, fileName: String, at: Instant): RestoreResult
}

/** The whole database as domain values, for writing a backup. */
data class BackupSnapshot(
    val accounts: List<Account>,
    val categories: List<Category>,
    val subcategories: List<Subcategory>,
    val transactions: List<Transaction>,
)

/** What a restore created. The transactions are in [batch], so Import history can reverse them. */
data class RestoreResult(
    val accountsCreated: Int,
    val categoriesCreated: Int,
    val subcategoriesCreated: Int,
    val batch: ImportBatch,
)

/** A parsed, fully mapped row waiting on the user's confirmation. */
data class PendingRow(
    val occurredAt: Instant,
    val type: TxnType,
    val amount: Money,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val note: String?,
    val dedupHash: String,
)

class DuplicateNameException(val existingId: Long, val name: String) :
    IllegalArgumentException("\"$name\" already exists")
