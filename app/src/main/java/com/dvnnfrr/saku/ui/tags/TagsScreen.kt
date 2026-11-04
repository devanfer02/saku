package com.dvnnfrr.saku.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/** More > Tags, building its own ViewModel. */
@Composable
fun TagsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: TagsViewModel = viewModel(factory = TagsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    TagsScreen(
        state = state,
        onOpen = viewModel::open,
        onDraft = viewModel::setDraft,
        onSaveName = viewModel::saveName,
        onToggleArchived = viewModel::toggleArchived,
        onClose = viewModel::close,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Every tag with its transaction count, live ones first. Tapping a row opens a bottom sheet
 * holding rename and archive, so both actions sit in the lower half of the screen.
 */
@Composable
fun TagsScreen(
    state: TagsUiState,
    onOpen: (Long) -> Unit,
    onDraft: (String) -> Unit,
    onSaveName: () -> Unit,
    onToggleArchived: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.tags_title),
            onBack = onBack,
            backLabel = stringResource(R.string.screen_back),
        )
        when {
            state.loading -> Unit
            state.isEmpty -> EmptyState(
                icon = Icons.AutoMirrored.Filled.Label,
                message = stringResource(R.string.tags_empty),
            )
            else -> TagList(state, onOpen)
        }
    }

    state.editing?.let { edit ->
        TagEditSheet(
            edit = edit,
            onDraft = onDraft,
            onSaveName = onSaveName,
            onToggleArchived = onToggleArchived,
            onClose = onClose,
        )
    }
}

@Composable
private fun TagList(state: TagsUiState, onOpen: (Long) -> Unit) {
    val spacing = SakuTheme.spacing
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.tags_edit_hint),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
                modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
            )
        }
        items(count = state.active.size, key = { state.active[it].id }) { TagRow(state.active[it], onOpen) }
        if (state.archived.isNotEmpty()) {
            item(key = "archived") {
                Text(
                    text = stringResource(R.string.tags_archived_header),
                    style = SakuTheme.typography.label,
                    color = SakuTheme.colors.ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screen, vertical = spacing.sm)
                        .semantics { heading() },
                )
            }
            items(count = state.archived.size, key = { state.archived[it].id }) { TagRow(state.archived[it], onOpen) }
        }
    }
}

/** One tag. The row is the touch target and TalkBack reads name and count as one item. */
@Composable
private fun TagRow(row: TagRowUi, onOpen: (Long) -> Unit) {
    val spacing = SakuTheme.spacing
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.tags_row_action)) { onOpen(row.id) }
                .defaultMinSize(minHeight = spacing.rowMinHeight)
                .padding(horizontal = spacing.screen, vertical = spacing.md)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = row.name,
                style = SakuTheme.typography.body,
                color = if (row.archived) SakuTheme.colors.ink2 else SakuTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.tags_row_count, row.count, row.count),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
            )
        }
        HorizontalDivider(color = SakuTheme.colors.line)
    }
}

/**
 * Rename and archive for one tag, as a bottom sheet so both are in thumb reach. A name in use
 * by another tag is refused under the field before Save is offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagEditSheet(
    edit: TagEditUi,
    onDraft: (String) -> Unit,
    onSaveName: () -> Unit,
    onToggleArchived: () -> Unit,
    onClose: () -> Unit,
) {
    val spacing = SakuTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SakuTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = edit.name,
                style = SakuTheme.typography.heading,
                color = SakuTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            OutlinedTextField(
                value = edit.draft,
                onValueChange = onDraft,
                singleLine = true,
                label = { Text(stringResource(R.string.tags_name_label)) },
                isError = edit.problem != null,
                supportingText = edit.problem?.let { problem -> { Text(problem.message()) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (edit.canSave) onSaveName() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSaveName,
                enabled = edit.canSave,
                shape = RoundedCornerShape(spacing.corner),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SakuTheme.colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
            ) { Text(stringResource(R.string.tags_save_name)) }

            HorizontalDivider(color = SakuTheme.colors.line)
            Text(
                text = stringResource(R.string.tags_archive_note),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
            )
            OutlinedButton(
                onClick = onToggleArchived,
                shape = RoundedCornerShape(spacing.corner),
                modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
            ) {
                Text(stringResource(if (edit.archived) R.string.tags_unarchive else R.string.tags_archive))
            }
        }
    }
}

@Composable
private fun RenameProblem.message(): String = when (this) {
    RenameProblem.Invalid -> stringResource(R.string.tags_name_invalid)
    is RenameProblem.Taken -> stringResource(R.string.tags_name_taken, name)
}

@SakuPreview
@Composable
private fun TagsScreenPreview() {
    SakuPreviewSurface(padded = false) {
        TagsScreen(
            state = TagsUiState(
                active = listOf(TagRowUi(1, "Monthly grocery", 14, false), TagRowUi(2, "Ramadan", 1, false)),
                archived = listOf(TagRowUi(3, "Trip Bali", 22, true)),
                loading = false,
            ),
            onOpen = {},
            onDraft = {},
            onSaveName = {},
            onToggleArchived = {},
            onClose = {},
            onBack = {},
        )
    }
}
