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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate

/**
 * Restore from backup: pick a file, read the preview, confirm.
 *
 * The preview is the whole point. It names every account whose settings differ from the
 * backup's, because restore keeps this phone's settings and the user should know which
 * figures will not change before tapping restore.
 */
@Composable
fun RestoreScreen(
    modifier: Modifier = Modifier,
    viewModel: RestoreViewModel = viewModel(factory = RestoreViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val resolver = LocalContext.current.contentResolver

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.load(uri?.let { ContentDocumentSource(resolver, it) })
    }

    RestoreScreen(
        state = state,
        modifier = modifier,
        onPickFile = { picker.launch(SPREADSHEET_MIME_TYPES) },
        onConfirm = viewModel::confirm,
        onDiscard = viewModel::discard,
    )
}

@Composable
fun RestoreScreen(
    state: RestoreUiState,
    modifier: Modifier = Modifier,
    onPickFile: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onDiscard: () -> Unit = {},
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(spacing.screen),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.restore_intro),
            style = SakuTheme.typography.body,
            color = colors.ink2,
        )

        when (state) {
            RestoreUiState.Idle -> PrimaryButton(
                label = stringResource(R.string.restore_choose_file),
                onClick = onPickFile,
                modifier = Modifier.testTag(RESTORE_PICK_TAG),
            )

            is RestoreUiState.Reading -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                CircularProgressIndicator(color = colors.accent)
                Text(
                    text = stringResource(R.string.restore_reading, state.fileName),
                    style = SakuTheme.typography.body,
                    color = colors.ink2,
                )
            }

            is RestoreUiState.Refused -> {
                Text(
                    text = state.refusal.message(state.fileName),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.expense,
                )
                ChooseAnother(onPickFile)
            }

            is RestoreUiState.Ready -> Preview(state, onConfirm = onConfirm, onDiscard = onDiscard)

            is RestoreUiState.Restored -> {
                Text(
                    text = stringResource(R.string.restore_done, state.fileName),
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
                    text = stringResource(R.string.restore_done_note),
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun Preview(state: RestoreUiState.Ready, onConfirm: () -> Unit, onDiscard: () -> Unit) {
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
        Fact(stringResource(R.string.restore_preview_file), preview.fileName)
        Fact(stringResource(R.string.restore_preview_written), preview.writtenOn.readable())
        Fact(
            stringResource(R.string.restore_preview_format),
            stringResource(R.string.restore_preview_format_value, preview.formatVersion, preview.appVersion),
        )
    }

    CountCard(
        buildList {
            add(stringResource(R.string.restore_new_accounts) to preview.newAccounts)
            add(stringResource(R.string.restore_new_categories) to preview.newCategories)
            add(stringResource(R.string.restore_new_transactions) to preview.newTransactions)
            add(stringResource(R.string.restore_already_present) to preview.alreadyPresent)
            if (preview.heldBack > 0) add(stringResource(R.string.restore_held_back) to preview.heldBack)
        },
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
            text = stringResource(R.string.restore_nothing_new),
            style = SakuTheme.typography.bodyStrong,
            color = colors.ink2,
        )
    }

    state.failure?.let {
        Text(
            text = stringResource(R.string.restore_failed, it),
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
            label = stringResource(R.string.restore_action),
            onClick = onConfirm,
            enabled = preview.hasAnythingNew && !state.committing,
            modifier = Modifier.testTag(RESTORE_CONFIRM_TAG),
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md),
    ) {
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = SakuTheme.typography.bodyStrong, color = SakuTheme.colors.ink)
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SakuTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = colors.surfaceAlt,
            disabledContentColor = colors.inkDisabled,
        ),
        modifier = modifier,
    ) {
        Text(text = label, style = SakuTheme.typography.bodyStrong)
    }
}

@Composable
private fun ChooseAnother(onPickFile: () -> Unit) {
    TextButton(onClick = onPickFile) {
        Text(
            text = stringResource(R.string.restore_choose_another),
            color = SakuTheme.colors.accent,
            style = SakuTheme.typography.bodyStrong,
        )
    }
}

/** For instrumentation, as IMPORT_ACTION_TAG. */
const val RESTORE_PICK_TAG = "restore_choose_file"
const val RESTORE_CONFIRM_TAG = "restore_confirm"

@SakuPreview
@Composable
private fun RestorePreviewPreview() {
    SakuPreviewSurface(padded = false) {
        RestoreScreen(
            state = RestoreUiState.Ready(
                RestorePreview(
                    fileName = "Saku backup 2026-10-30.xlsx",
                    writtenOn = LocalDate(2026, 10, 30),
                    formatVersion = 1,
                    appVersion = "1.0",
                    newAccounts = 2,
                    newCategories = 0,
                    newSubcategories = 3,
                    newTransactions = 1_300,
                    alreadyPresent = 0,
                    heldBack = 0,
                    conflicts = listOf(
                        AccountConflict("BCA", listOf(AccountSetting.OPENING_BALANCE, AccountSetting.OPENING_DATE)),
                    ),
                ),
            ),
        )
    }
}
