package com.dvnnfrr.saku.excel

import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/*
 * The boundary between a SQLite file in Saku's schema and the backup restore.
 *
 * The rows below are the file's columns as SQLite returns them, enums still as text and
 * times still as epoch seconds. [DatabaseImport.toBackup] turns them into the records a
 * backup restore reads, so a database file merges exactly as a backup does. No Android
 * and no SQLite type crosses this file; `DatabaseFileReader` does the I/O.
 */

/** What a database file says about itself before a single row is read. */
data class DatabaseHeader(
    val userVersion: Int,
    /** Null when the file has no `room_master_table`, so Room never wrote it. */
    val identityHash: String?,
)

data class DatabaseAccountRow(
    val id: Long,
    val name: String,
    val type: String,
    val openingBalance: Long,
    val openingAsOfEpochSec: Long,
    val colorSlot: Int,
    val sortOrder: Int,
    val archived: Boolean,
    val lastReconciledEpochSec: Long?,
)

data class DatabaseCategoryRow(
    val id: Long,
    val name: String,
    val kind: String,
    val colorSlot: Int,
    val sortOrder: Int,
    val archived: Boolean,
)

data class DatabaseSubcategoryRow(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
)

data class DatabaseTxnRow(
    val id: Long,
    val occurredAtEpochSec: Long,
    val type: String,
    val amountIdr: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val note: String?,
)

data class DatabaseRows(
    val accounts: List<DatabaseAccountRow>,
    val categories: List<DatabaseCategoryRow>,
    val subcategories: List<DatabaseSubcategoryRow>,
    val transactions: List<DatabaseTxnRow>,
)

/** Why a database file was not imported. Data, not prose; the screen words it. */
sealed interface DatabaseRefusal {
    /** No `room_master_table`: Room did not write this file. */
    data object NotSaku : DatabaseRefusal

    /** Written for another schema version of Saku. */
    data class DifferentVersion(val found: Int, val expected: Int) : DatabaseRefusal

    /** The right version number over the wrong tables: the file was built from another schema. */
    data object DifferentLayout : DatabaseRefusal

    /** [id] is the row's own primary key, so the person who built the file can find it. */
    data class InvalidValue(val table: String, val id: Long, val column: String) : DatabaseRefusal

    /** A row points at an id its table does not hold. */
    data class MissingReference(val table: String, val id: Long, val column: String) : DatabaseRefusal

    data class DuplicateName(val table: String, val name: String) : DatabaseRefusal

    /** A transaction whose category kind is not the one its type resolves to by name. */
    data class CategoryKindMismatch(val id: Long) : DatabaseRefusal
}

sealed interface DatabaseReadOutcome {
    data class Read(val contents: BackupContents) : DatabaseReadOutcome

    data class Refused(val refusal: DatabaseRefusal) : DatabaseReadOutcome
}

object DatabaseImport {

    const val ACCOUNT = "account"
    const val CATEGORY = "category"
    const val SUBCATEGORY = "subcategory"
    const val TXN = "txn"

    /**
     * Null when the file was written for [expectedVersion] with [expectedHash]. Checked
     * before any row is read, because a file of another schema may not have the columns
     * the row queries name.
     */
    fun refusalFor(header: DatabaseHeader, expectedVersion: Int, expectedHash: String): DatabaseRefusal? =
        when {
            header.identityHash == null -> DatabaseRefusal.NotSaku
            header.userVersion != expectedVersion ->
                DatabaseRefusal.DifferentVersion(header.userVersion, expectedVersion)
            header.identityHash != expectedHash -> DatabaseRefusal.DifferentLayout
            else -> null
        }

    /**
     * The file's rows as the records a backup restore reads.
     *
     * Ids stop here: accounts, categories and subcategories are keyed by name, as a
     * backup is, so they merge into what this phone already has. Opening balances,
     * colours, sort orders, archived flags and reconcile dates carry over verbatim; an
     * adjustment keeps its sign. Amount rules are left to the commit validator.
     */
    fun toBackup(rows: DatabaseRows, userVersion: Int): DatabaseReadOutcome {
        return try {
            DatabaseReadOutcome.Read(adapt(rows, userVersion))
        } catch (refused: Refusal) {
            DatabaseReadOutcome.Refused(refused.refusal)
        }
    }

    private class Refusal(val refusal: DatabaseRefusal) : Exception()

    private fun refuse(refusal: DatabaseRefusal): Nothing = throw Refusal(refusal)

    private fun adapt(rows: DatabaseRows, userVersion: Int): BackupContents {
        val accounts = rows.accounts.map { it.asBackup() }
        val categories = rows.categories.map { it.asBackup() }
        refuseDuplicates(ACCOUNT, accounts.map { it.name to nameKey(it.name) })
        // A category name is unique within its kind only, so the key carries the kind.
        refuseDuplicates(CATEGORY, categories.map { it.name to it.ref })

        val accountById = rows.accounts.associateBy { it.id }
        val categoryById = rows.categories.zip(categories).associate { (row, category) -> row.id to category }

        val subcategories = rows.subcategories.map { row ->
            val parent = categoryById[row.categoryId]
                ?: refuse(DatabaseRefusal.MissingReference(SUBCATEGORY, row.id, "categoryId"))
            BackupSubcategory(
                categoryName = parent.name,
                categoryKind = parent.kind,
                name = row.name,
                sortOrder = row.sortOrder,
                archived = row.archived,
            )
        }
        val subcategoryById = rows.subcategories.zip(subcategories).associate { (row, sub) -> row.id to (row to sub) }
        refuseDuplicates(SUBCATEGORY, subcategories.map { it.name to it.ref })

        val parsed = rows.transactions.map { row ->
            val type = TxnType.entries.firstOrNull { it.name == row.type }
                ?: refuse(DatabaseRefusal.InvalidValue(TXN, row.id, "type"))
            val account = accountById[row.accountId]
                ?: refuse(DatabaseRefusal.MissingReference(TXN, row.id, "accountId"))
            val toAccount = row.toAccountId?.let {
                accountById[it] ?: refuse(DatabaseRefusal.MissingReference(TXN, row.id, "toAccountId"))
            }
            val category = row.categoryId?.let {
                categoryById[it] ?: refuse(DatabaseRefusal.MissingReference(TXN, row.id, "categoryId"))
            }
            val subcategory = row.subcategoryId?.let { id ->
                val (subRow, sub) = subcategoryById[id]
                    ?: refuse(DatabaseRefusal.MissingReference(TXN, row.id, "subcategoryId"))
                val belongsToCategory = subRow.categoryId == row.categoryId
                if (!belongsToCategory) refuse(DatabaseRefusal.InvalidValue(TXN, row.id, "subcategoryId"))
                sub
            }
            val parsedRow = ParsedRow(
                // The workbook row number has no meaning here; the file's own id does.
                sourceRow = row.id.toInt(),
                occurredAt = Instant.fromEpochSeconds(row.occurredAtEpochSec),
                type = type,
                amount = Money(row.amountIdr),
                accountName = account.name,
                toAccountName = toAccount?.name,
                categoryName = category?.name,
                subcategoryName = subcategory?.name,
                note = row.note,
                discrepancy = null,
            )
            Triple(row.id, parsedRow, category)
        }

        val contents = BackupContents(
            // A database records no write time, app version or zone. Nothing on this path
            // reads them: times are epoch seconds and the preview has its own shape.
            info = BackupInfo(
                formatVersion = userVersion,
                appVersion = "",
                writtenAt = Instant.fromEpochSeconds(0),
                zone = TimeZone.UTC,
            ),
            accounts = accounts,
            categories = categories,
            subcategories = subcategories,
            transactions = ParsedSheet(
                layout = SheetLayout.SAKU,
                sheetName = TXN,
                fileRowCount = rows.transactions.size,
                rows = parsed.map { it.second },
                rejected = emptyList(),
            ),
        )

        // The restore resolves a row's category by name and row type. The file knows the
        // real kind, so a row where the two disagree would land in the wrong category.
        parsed.forEach { (id, row, category) ->
            if (category == null) return@forEach
            val resolvesToItsOwnCategory = contents.categoryRefOf(row) == category.ref
            if (!resolvesToItsOwnCategory) refuse(DatabaseRefusal.CategoryKindMismatch(id))
        }
        return contents
    }

    private fun DatabaseAccountRow.asBackup(): BackupAccount = BackupAccount(
        name = name,
        type = AccountType.entries.firstOrNull { it.name == type }
            ?: refuse(DatabaseRefusal.InvalidValue(ACCOUNT, id, "type")),
        openingBalance = Money(openingBalance),
        openingAsOf = Instant.fromEpochSeconds(openingAsOfEpochSec),
        colorSlot = colorSlotOf(colorSlot) ?: refuse(DatabaseRefusal.InvalidValue(ACCOUNT, id, "colorSlot")),
        sortOrder = sortOrder,
        archived = archived,
        lastReconciled = lastReconciledEpochSec?.let(Instant::fromEpochSeconds),
    )

    private fun DatabaseCategoryRow.asBackup(): BackupCategory = BackupCategory(
        name = name,
        kind = CategoryKind.entries.firstOrNull { it.name == kind }
            ?: refuse(DatabaseRefusal.InvalidValue(CATEGORY, id, "kind")),
        colorSlot = colorSlotOf(colorSlot) ?: refuse(DatabaseRefusal.InvalidValue(CATEGORY, id, "colorSlot")),
        sortOrder = sortOrder,
        archived = archived,
    )

    private fun colorSlotOf(index: Int): ColorSlot? =
        if (index in 0 until ColorSlot.COUNT) ColorSlot(index) else null

    /** [named] pairs the name to show with the key it must be unique on. */
    private fun refuseDuplicates(table: String, named: List<Pair<String, Any>>) {
        val seen = mutableSetOf<Any>()
        named.forEach { (name, key) ->
            if (!seen.add(key)) refuse(DatabaseRefusal.DuplicateName(table, name))
        }
    }
}
