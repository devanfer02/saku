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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
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
        onChooseDestination = { picker.launch(viewModel.suggestedFileName()) },
    )
}

@Composable
fun BackupScreen(
    state: BackupUiState,
    modifier: Modifier = Modifier,
    onChooseDestination: () -> Unit = {},
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
            text = stringResource(R.string.backup_intro),
            style = SakuTheme.typography.body,
            color = colors.ink2,
        )

        Button(
            onClick = onChooseDestination,
            enabled = state !is BackupUiState.Writing,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = colors.surfaceAlt,
                disabledContentColor = colors.inkDisabled,
            ),
            modifier = Modifier.fillMaxWidth().testTag(BACKUP_ACTION_TAG),
        ) {
            Text(text = stringResource(R.string.backup_action), style = SakuTheme.typography.bodyStrong)
        }

        when (state) {
            BackupUiState.Idle -> Unit

            is BackupUiState.Writing -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                CircularProgressIndicator(color = colors.accent)
                Text(
                    text = stringResource(R.string.backup_writing, state.fileName),
                    style = SakuTheme.typography.body,
                    color = colors.ink2,
                )
            }

            is BackupUiState.Written -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = stringResource(R.string.backup_done, state.fileName),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.income,
                )
                CountCard(
                    listOf(
                        stringResource(R.string.backup_count_accounts) to state.counts.accounts,
                        stringResource(R.string.backup_count_categories) to state.counts.categories,
                        stringResource(R.string.backup_count_subcategories) to state.counts.subcategories,
                        stringResource(R.string.backup_count_transactions) to state.counts.transactions,
                    ),
                )
            }

            is BackupUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResource(R.string.backup_failed, state.fileName),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.expense,
                )
                state.detail?.let {
                    Text(text = it, style = SakuTheme.typography.caption, color = colors.ink3)
                }
            }
        }

        Text(
            text = stringResource(R.string.backup_not_encrypted),
            style = SakuTheme.typography.caption,
            color = colors.ink3,
        )
        Text(
            text = stringResource(R.string.export_local_only),
            style = SakuTheme.typography.caption,
            color = colors.ink3,
        )
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
                counts = BackupCounts(accounts = 6, categories = 19, subcategories = 7, transactions = 1_300),
            ),
        )
    }
}
