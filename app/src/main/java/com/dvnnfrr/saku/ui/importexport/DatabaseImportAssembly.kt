package com.dvnnfrr.saku.ui.importexport

import com.dvnnfrr.saku.data.repository.RestoreResult
import com.dvnnfrr.saku.excel.AccountConflict
import com.dvnnfrr.saku.excel.DatabaseRefusal
import com.dvnnfrr.saku.excel.ImportPlan
import com.dvnnfrr.saku.excel.RestorePlan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Everything the database import preview shows. Built by [databaseImportPreview], never by hand. */
data class DatabaseImportPreview(
    val fileName: String,
    val accountsInFile: Int,
    val categoriesInFile: Int,
    val transactionsInFile: Int,
    /** Null when the file holds no transactions. */
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val newAccounts: Int,
    val newCategories: Int,
    /** Not shown, as in [RestorePreview]; counted so a file adding only these can still confirm. */
    val newSubcategories: Int,
    val newTransactions: Int,
    val alreadyPresent: Int,
    val conflicts: List<AccountConflict>,
) {
    /** False when importing would add nothing, which is the second import of one file. */
    val hasAnythingNew: Boolean
        get() = newAccounts + newCategories + newSubcategories + newTransactions > 0
}

sealed interface DatabaseImportRefusal {
    /** Not a SQLite file, or one whose tables could not be read. */
    data class Unreadable(val detail: String?) : DatabaseImportRefusal

    data class Database(val refusal: DatabaseRefusal) : DatabaseImportRefusal
}

sealed interface DatabaseImportUiState {
    data object Idle : DatabaseImportUiState

    data class Reading(val fileName: String) : DatabaseImportUiState

    data class Refused(val fileName: String, val refusal: DatabaseImportRefusal) : DatabaseImportUiState

    /** [failure] is the commit's own message; nothing was written when it is set. */
    data class Ready(
        val preview: DatabaseImportPreview,
        val committing: Boolean = false,
        val failure: String? = null,
    ) : DatabaseImportUiState

    data class Imported(val fileName: String, val result: RestoreResult) : DatabaseImportUiState
}

/**
 * The preview, from the restore plan and the stored hashes.
 *
 * The transaction tally is [ImportPlan.tally], as in [restorePreview], so "new
 * transactions" is exactly what the restore will insert. Dates are read in [zone], the
 * phone's, because that is the calendar the user will see the rows on.
 */
fun databaseImportPreview(
    fileName: String,
    plan: RestorePlan,
    hashes: List<String>,
    existingHashes: Set<String>,
    zone: TimeZone,
): DatabaseImportPreview {
    val backup = plan.backup
    val rows = backup.transactions.rows
    val tally = ImportPlan.tally(backup.transactions, hashes, existingHashes, excluded = 0)
    return DatabaseImportPreview(
        fileName = fileName,
        accountsInFile = backup.accounts.size,
        categoriesInFile = backup.categories.size,
        transactionsInFile = rows.size,
        firstDate = rows.minOfOrNull { it.occurredAt }?.toLocalDateTime(zone)?.date,
        lastDate = rows.maxOfOrNull { it.occurredAt }?.toLocalDateTime(zone)?.date,
        newAccounts = plan.accountsToCreate.size,
        newCategories = plan.categoriesToCreate.size,
        newSubcategories = plan.subcategoriesToCreate.size,
        newTransactions = tally.insertable,
        alreadyPresent = tally.alreadyPresent,
        conflicts = plan.conflicts,
    )
}
