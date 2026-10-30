package com.dvnnfrr.saku.ui.importexport

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.excel.AccountConflict
import com.dvnnfrr.saku.excel.AccountSetting
import com.dvnnfrr.saku.excel.BackupRefusal

/*
 * Where the backup model meets copy, as ImportStrings.kt is for import. Each mapping is
 * exhaustive, so a new refusal or setting without copy fails to compile.
 */

@Composable
fun RestoreRefusal.message(fileName: String): String = when (this) {
    is RestoreRefusal.Unreadable -> stringResource(R.string.restore_refused_unreadable, fileName)
    is RestoreRefusal.Backup -> refusal.message(fileName)
}

@Composable
private fun BackupRefusal.message(fileName: String): String = when (this) {
    is BackupRefusal.MissingSheet -> stringResource(R.string.restore_refused_missing_sheet, fileName, sheetName)
    is BackupRefusal.NewerFormat -> if (appVersion.isNullOrBlank()) {
        stringResource(R.string.restore_refused_newer_unknown, fileName, formatVersion)
    } else {
        stringResource(R.string.restore_refused_newer, fileName, appVersion, formatVersion)
    }

    is BackupRefusal.MissingColumn ->
        stringResource(R.string.restore_refused_missing_column, fileName, sheetName, column)

    is BackupRefusal.InvalidCell ->
        stringResource(R.string.restore_refused_invalid_cell, fileName, sheetName, column, row)

    is BackupRefusal.DuplicateName ->
        stringResource(R.string.restore_refused_duplicate, fileName, sheetName, name)

    is BackupRefusal.UnknownName ->
        stringResource(R.string.restore_refused_unknown_name, fileName, sheetName, name)

    BackupRefusal.UnrecognisedTransactions -> stringResource(R.string.restore_refused_transactions, fileName)
}

@Composable
fun AccountConflict.line(): String =
    stringResource(
        R.string.restore_conflict_line,
        name,
        settings.map { it.label() }.joinToString(", "),
    )

@Composable
private fun AccountSetting.label(): String = stringResource(
    when (this) {
        AccountSetting.TYPE -> R.string.restore_setting_type
        AccountSetting.OPENING_BALANCE -> R.string.restore_setting_opening_balance
        AccountSetting.OPENING_DATE -> R.string.restore_setting_opening_date
        AccountSetting.COLOUR -> R.string.restore_setting_colour
        AccountSetting.ARCHIVED -> R.string.restore_setting_archived
        AccountSetting.LAST_RECONCILED -> R.string.restore_setting_last_reconciled
    },
)
