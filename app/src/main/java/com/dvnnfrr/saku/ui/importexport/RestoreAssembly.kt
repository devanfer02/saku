package com.dvnnfrr.saku.ui.importexport

import com.dvnnfrr.saku.data.repository.RestoreResult
import com.dvnnfrr.saku.excel.AccountConflict
import com.dvnnfrr.saku.excel.BackupRefusal
import com.dvnnfrr.saku.excel.ImportPlan
import com.dvnnfrr.saku.excel.RestorePlan
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Everything the restore preview shows. Built by [restorePreview], never by hand. */
data class RestorePreview(
    val fileName: String,
    val writtenOn: LocalDate,
    val formatVersion: Int,
    val appVersion: String,
    val newAccounts: Int,
    val newCategories: Int,
    val newSubcategories: Int,
    val newTransactions: Int,
    val alreadyPresent: Int,
    val heldBack: Int,
    val conflicts: List<AccountConflict>,
) {
    /** False when restoring would add nothing, which is the second restore of one file. */
    val hasAnythingNew: Boolean
        get() = newAccounts + newCategories + newSubcategories + newTransactions > 0
}

sealed interface RestoreRefusal {
    data class Unreadable(val detail: String?) : RestoreRefusal

    data class Backup(val refusal: BackupRefusal) : RestoreRefusal
}

sealed interface RestoreUiState {
    data object Idle : RestoreUiState

    data class Reading(val fileName: String) : RestoreUiState

    data class Refused(val fileName: String, val refusal: RestoreRefusal) : RestoreUiState

    /** [failure] is the commit's own message; nothing was written when it is set. */
    data class Ready(
        val preview: RestorePreview,
        val committing: Boolean = false,
        val failure: String? = null,
    ) : RestoreUiState

    data class Restored(val fileName: String, val result: RestoreResult) : RestoreUiState
}

/**
 * The preview counts, from the plan and the stored hashes.
 *
 * The transaction tally is [ImportPlan.tally], the same arithmetic the import preview uses
 * and the same order `commit` filters in, so "new transactions" here is what the restore
 * will insert.
 */
fun restorePreview(
    fileName: String,
    plan: RestorePlan,
    hashes: List<String>,
    existingHashes: Set<String>,
    zone: TimeZone,
): RestorePreview {
    val backup = plan.backup
    val tally = ImportPlan.tally(backup.transactions, hashes, existingHashes, excluded = 0)
    return RestorePreview(
        fileName = fileName,
        writtenOn = backup.info.writtenAt.toLocalDateTime(zone).date,
        formatVersion = backup.info.formatVersion,
        appVersion = backup.info.appVersion,
        newAccounts = plan.accountsToCreate.size,
        newCategories = plan.categoriesToCreate.size,
        newSubcategories = plan.subcategoriesToCreate.size,
        newTransactions = tally.insertable,
        alreadyPresent = tally.alreadyPresent,
        heldBack = tally.heldBack,
        conflicts = plan.conflicts,
    )
}
