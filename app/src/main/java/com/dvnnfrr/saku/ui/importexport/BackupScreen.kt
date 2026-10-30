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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.PickCard
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.shared.ThumbZonePane
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * Back up everything, through `ACTION_CREATE_DOCUMENT` and nowhere else.
 *
 * A cancelled picker hands back null, which the view model ignores: backing out is not an
 * error and the screen says nothing about it.
 */
@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onContinue: () -> Unit = {},
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val resolver = LocalContext.current.contentResolver

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XLSX_MIME_TYPE),
    ) { uri ->
        viewModel.backUp(uri?.let { ContentDocumentSink(resolver, it) })
    }

    BackupScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onChooseDestination = { picker.launch(viewModel.suggestedFileName()) },
        onContinue = onContinue,
    )
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onChooseDestination: () -> Unit = {},
    /** After a written backup: the host decides where the user goes next. */
    onContinue: () -> Unit = {},
) {
    val colors = SakuTheme.colors

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        ScreenHeader(title = stringResource(R.string.more_backup), onBack = onBack, backLabel = stringResource(R.string.screen_back))
        if (state is BackupUiState.Written) {
            // The card's job is done, so the result takes its place; another copy is a
            // link inside the result, and Continue sits at the bottom.
            ResultLayout(onContinue = onContinue) {
                ResultHeadline(stringResource(R.string.backup_done, state.fileName))
                CountCard(
                    listOf(
                        stringResource(R.string.backup_count_accounts) to state.counts.accounts,
                        stringResource(R.string.backup_count_categories) to state.counts.categories,
                        stringResource(R.string.backup_count_transactions) to state.counts.transactions,
                    ),
                )
                PrivacyNote(stringResource(R.string.backup_not_encrypted))
                LinkButton(stringResource(R.string.result_save_again), onChooseDestination)
            }
        } else {
            ThumbZonePane(modifier = Modifier.weight(1f)) {
                PickCard(
                    title = stringResource(R.string.backup_pick_title),
                    message = stringResource(R.string.backup_intro),
                    icon = Icons.Outlined.FolderOpen,
                    actionLabel = stringResource(R.string.backup_action),
                    onAction = onChooseDestination,
                    zoneLabel = if (state is BackupUiState.Writing) {
                        stringResource(R.string.backup_writing, state.fileName)
                    } else {
                        stringResource(R.string.backup_pick_zone)
                    },
                    busy = state is BackupUiState.Writing,
                    actionModifier = Modifier.testTag(BACKUP_ACTION_TAG),
                    notes = {
                        PrivacyNote(stringResource(R.string.backup_not_encrypted))
                        PrivacyNote(stringResource(R.string.export_local_only))
                    },
                )

                if (state is BackupUiState.Failed) {
                    WriteFailure(
                        message = stringResource(R.string.backup_failed, state.fileName),
                        detail = state.detail,
                    )
                }
            }
        }
    }
}

/** A caption under a save card's button: what happens to the file once it is written. */
@Composable
internal fun PrivacyNote(text: String) {
    Text(text = text, style = SakuTheme.typography.caption, color = SakuTheme.colors.ink2)
}

/** A write that did not finish, under the save card. Shared by backup and export. */
@Composable
internal fun WriteFailure(message: String, detail: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
    ) {
        Text(text = message, style = SakuTheme.typography.bodyStrong, color = SakuTheme.colors.expense)
        detail?.let {
            Text(text = it, style = SakuTheme.typography.caption, color = SakuTheme.colors.ink2)
        }
    }
}

/** Label and count rows on a surface card. Shared by the backup result and the restore preview. */
@Composable
internal fun CountCard(rows: List<Pair<String, Int>>) {
    val spacing = SakuTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SakuTheme.colors.surface, RoundedCornerShape(spacing.corner))
            .padding(vertical = spacing.xs),
    ) {
        rows.forEach { (label, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = label, style = SakuTheme.typography.body, color = SakuTheme.colors.ink2)
                Text(text = count.toString(), style = SakuTheme.typography.bodyStrong, color = SakuTheme.colors.ink)
            }
        }
    }
}

/** For instrumentation, as EXPORT_ACTION_TAG. */
const val BACKUP_ACTION_TAG = "backup_choose_destination"

@SakuPreview
@Composable
private fun BackupWrittenPreview() {
    SakuPreviewSurface(padded = false) {
        BackupScreen(
            state = BackupUiState.Written(
                fileName = "Saku backup 2026-10-30.xlsx",
                counts = BackupCounts(accounts = 6, categories = 19, transactions = 1_300),
            ),
        )
    }
}

@SakuPreview
@Composable
private fun BackupIdlePreview() {
    SakuPreviewSurface(padded = false) { BackupScreen(state = BackupUiState.Idle) }
}

@SakuPreview
@Composable
private fun BackupWritingPreview() {
    SakuPreviewSurface(padded = false) {
        BackupScreen(state = BackupUiState.Writing(fileName = "Saku backup 2026-10-30.xlsx"))
    }
}
