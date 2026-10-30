package com.dvnnfrr.saku.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.excel.AccountConflict
import com.dvnnfrr.saku.excel.AccountSetting
import com.dvnnfrr.saku.excel.DatabaseRefusal
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate

/**
 * Import database (.db): pick a SQLite file in Saku's schema, read the preview, confirm.
 *
 * The merge is the backup restore's, so the preview names every account whose settings
 * differ, because the import keeps this phone's settings for those.
 */
@Composable
fun DatabaseImportScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: DatabaseImportViewModel = viewModel(factory = DatabaseImportViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val resolver = LocalContext.current.contentResolver

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.load(uri?.let { ContentDocumentSource(resolver, it) })
    }

    DatabaseImportScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onPickFile = { picker.launch(DATABASE_MIME_TYPES) },
        onConfirm = viewModel::confirm,
        onDiscard = viewModel::discard,
    )
}

@Composable
fun DatabaseImportScreen(
    state: DatabaseImportUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onPickFile: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onDiscard: () -> Unit = {},
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = stringResource(R.string.more_import_database), onBack = onBack, backLabel = stringResource(R.string.screen_back))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(spacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.database_import_intro),
                style = SakuTheme.typography.body,
                color = colors.ink2,
            )

            when (state) {
                DatabaseImportUiState.Idle -> PrimaryButton(
                    label = stringResource(R.string.database_import_choose_file),
                    onClick = onPickFile,
                    modifier = Modifier.testTag(DATABASE_IMPORT_PICK_TAG),
                )

                is DatabaseImportUiState.Reading -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    CircularProgressIndicator(color = colors.accent)
                    Text(
                        text = stringResource(R.string.database_import_reading, state.fileName),
                        style = SakuTheme.typography.body,
                        color = colors.ink2,
                    )
                }

                is DatabaseImportUiState.Refused -> {
                    Text(
                        text = state.refusal.message(state.fileName),
                        style = SakuTheme.typography.bodyStrong,
                        color = colors.expense,
                    )
                    ChooseAnother(onPickFile)
                }

                is DatabaseImportUiState.Ready -> Preview(state, onConfirm = onConfirm, onDiscard = onDiscard)

                is DatabaseImportUiState.Imported -> {
                    Text(
                        text = stringResource(R.string.database_import_done, state.fileName),
                        style = SakuTheme.typography.bodyStrong,
                        color = colors.income,
                    )
                    CountCard(
                        listOf(
                            stringResource(R.string.restore_new_accounts) to state.result.accountsCreated,
                            stringResource(R.string.restore_new_categories) to state.result.categoriesCreated,
                            stringResource(R.string.restore_new_transactions) to state.result.batch.insertedCount,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.database_import_done_note),
                        style = SakuTheme.typography.caption,
                        color = colors.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun Preview(state: DatabaseImportUiState.Ready, onConfirm: () -> Unit, onDiscard: () -> Unit) {
    val preview = state.preview
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(spacing.corner))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Fact(stringResource(R.string.database_import_preview_file), preview.fileName)
        Fact(stringResource(R.string.database_import_in_file_accounts), preview.accountsInFile.toString())
        Fact(stringResource(R.string.database_import_in_file_categories), preview.categoriesInFile.toString())
        Fact(stringResource(R.string.database_import_in_file_transactions), preview.transactionsInFile.toString())
        Fact(stringResource(R.string.database_import_dates), preview.dateRangeLabel())
    }

    CountCard(
        listOf(
            stringResource(R.string.restore_new_accounts) to preview.newAccounts,
            stringResource(R.string.restore_new_categories) to preview.newCategories,
            stringResource(R.string.restore_new_transactions) to preview.newTransactions,
            stringResource(R.string.restore_already_present) to preview.alreadyPresent,
        ),
    )

    if (preview.conflicts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(R.string.restore_conflicts_title),
                style = SakuTheme.typography.bodyStrong,
                color = colors.ink,
            )
            preview.conflicts.forEach { conflict ->
                Text(text = conflict.line(), style = SakuTheme.typography.body, color = colors.ink2)
            }
            Text(
                text = stringResource(R.string.restore_conflicts_note),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
        }
    }

    if (!preview.hasAnythingNew) {
        Text(
            text = stringResource(R.string.database_import_nothing_new),
            style = SakuTheme.typography.bodyStrong,
            color = colors.ink2,
        )
    }

    state.failure?.let {
        Text(
            text = stringResource(R.string.database_import_failed, it),
            style = SakuTheme.typography.bodyStrong,
            color = colors.expense,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.committing) CircularProgressIndicator(color = colors.accent)
        TextButton(onClick = onDiscard, enabled = !state.committing) {
            Text(
                text = stringResource(R.string.restore_discard),
                style = SakuTheme.typography.bodyStrong,
                color = colors.ink2,
            )
        }
        PrimaryButton(
            label = stringResource(R.string.database_import_action),
            onClick = onConfirm,
            enabled = preview.hasAnythingNew && !state.committing,
            modifier = Modifier.testTag(DATABASE_IMPORT_CONFIRM_TAG),
        )
    }
}

@Composable
private fun DatabaseImportPreview.dateRangeLabel(): String {
    val first = firstDate ?: return stringResource(R.string.database_import_dates_none)
    val last = lastDate ?: first
    return stringResource(R.string.database_import_dates_value, first.readable(), last.readable())
}

/* Each mapping is exhaustive, so a new refusal without copy fails to compile. */

@Composable
private fun DatabaseImportRefusal.message(fileName: String): String = when (this) {
    is DatabaseImportRefusal.Unreadable -> stringResource(R.string.database_import_refused_unreadable, fileName)
    is DatabaseImportRefusal.Database -> refusal.message(fileName)
}

@Composable
private fun DatabaseRefusal.message(fileName: String): String = when (this) {
    DatabaseRefusal.NotSaku -> stringResource(R.string.database_import_refused_not_saku, fileName)
    is DatabaseRefusal.DifferentVersion ->
        stringResource(R.string.database_import_refused_version, fileName, found, expected)

    DatabaseRefusal.DifferentLayout -> stringResource(R.string.database_import_refused_layout, fileName)
    is DatabaseRefusal.InvalidValue ->
        stringResource(R.string.database_import_refused_invalid, fileName, table, column, id.toInt())

    is DatabaseRefusal.MissingReference ->
        stringResource(R.string.database_import_refused_missing, fileName, table, column, id.toInt())

    is DatabaseRefusal.DuplicateName ->
        stringResource(R.string.database_import_refused_duplicate, fileName, table, name)

    is DatabaseRefusal.CategoryKindMismatch ->
        stringResource(R.string.database_import_refused_kind, fileName, id.toInt())
}

/**
 * The MIME types the picker filters on. Providers report a `.db` file under any of these,
 * and some under none, so the generic one is included and the file itself is checked.
 */
val DATABASE_MIME_TYPES = arrayOf(
    "application/vnd.sqlite3",
    "application/x-sqlite3",
    "application/octet-stream",
)

/** For instrumentation, as RESTORE_PICK_TAG. */
const val DATABASE_IMPORT_PICK_TAG = "database_import_choose_file"
const val DATABASE_IMPORT_CONFIRM_TAG = "database_import_confirm"

@SakuPreview
@Composable
private fun DatabaseImportPreviewPreview() {
    SakuPreviewSurface(padded = false) {
        DatabaseImportScreen(
            state = DatabaseImportUiState.Ready(
                DatabaseImportPreview(
                    fileName = "saku.db",
                    accountsInFile = 4,
                    categoriesInFile = 12,
                    transactionsInFile = 1_200,
                    firstDate = LocalDate(2025, 1, 1),
                    lastDate = LocalDate(2025, 12, 31),
                    newAccounts = 3,
                    newCategories = 12,
                    newSubcategories = 0,
                    newTransactions = 1_200,
                    alreadyPresent = 0,
                    conflicts = listOf(AccountConflict("Cash", listOf(AccountSetting.OPENING_BALANCE))),
                ),
            ),
        )
    }
}
