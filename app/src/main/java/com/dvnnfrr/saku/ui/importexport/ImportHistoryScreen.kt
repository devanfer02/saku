package com.dvnnfrr.saku.ui.importexport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ImportBatch
import com.dvnnfrr.saku.ui.shared.ConfirmDialog
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Every import that ever ran, and a way to undo one.
 *
 * A reversed batch stays on the list rather than disappearing. The point of the record
 * is to answer "where did these transactions come from", and a batch that vanishes when
 * undone cannot answer "why are they gone".
 */
@Composable
fun ImportHistoryScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    viewModel: ImportHistoryViewModel = viewModel(factory = ImportHistoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()

    ImportHistoryBody(
        state = state,
        zone = zone,
        modifier = modifier,
        onBack = onBack,
        onReverse = viewModel::askToReverse,
        onConfirm = viewModel::confirmReversal,
        onDismiss = viewModel::dismissConfirmation,
    )
}

@Composable
fun ImportHistoryBody(
    state: ImportHistoryUiState,
    zone: TimeZone,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onReverse: (ImportBatch) -> Unit = {},
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val empty = state.batches.isEmpty() && !state.loading

    Column(modifier = modifier.fillMaxSize().background(SakuTheme.colors.background)) {
        ScreenHeader(title = stringResource(R.string.more_import_history), onBack = onBack, backLabel = stringResource(R.string.screen_back))
        if (empty) {
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.ListAlt,
                message = stringResource(R.string.import_history_empty),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(state.batches, key = { it.id }) { batch ->
                    BatchRow(batch = batch, zone = zone, onReverse = { onReverse(batch) })
                    HorizontalDivider(color = SakuTheme.colors.line)
                }
            }
        }
    }

    state.confirming?.let { batch ->
        ConfirmDialog(
            title = stringResource(R.string.import_history_reverse_title),
            body = stringResource(R.string.import_history_reverse_body),
            affectedCount = batch.insertedCount,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmLabel = stringResource(R.string.import_history_reverse),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun BatchRow(batch: ImportBatch, zone: TimeZone, onReverse: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val imported = batch.importedAt.toLocalDateTime(zone).date

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = batch.fileName,
                style = SakuTheme.typography.bodyStrong,
                color = if (batch.isReversed) colors.ink2 else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = imported.readable(),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
            Text(
                text = stringResource(
                    R.string.import_history_counts,
                    batch.insertedCount,
                    batch.skippedCount,
                    batch.rowCount,
                ),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
            )
            batch.reversedAt?.let {
                Text(
                    text = stringResource(
                        R.string.import_history_reversed,
                        it.toLocalDateTime(zone).date.readable(),
                    ),
                    style = SakuTheme.typography.caption,
                    color = colors.warning,
                )
            }
        }

        // Reversing twice is a no-op in the repository, but offering the action on an
        // already-reversed batch invites the user to try and be told nothing happened.
        if (!batch.isReversed) {
            TextButton(onClick = onReverse) {
                Text(
                    text = stringResource(R.string.import_history_reverse),
                    style = SakuTheme.typography.bodyStrong,
                    color = colors.expense,
                )
            }
        }
    }
}
