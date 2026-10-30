package com.dvnnfrr.saku.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.ui.shared.CategoryPicker
import com.dvnnfrr.saku.ui.shared.ConfirmDialog
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlot

/** Everything the screen can put in front of the list. One switch, so only one can be up. */
@Composable
fun CategoryDialogHost(
    dialog: CategoryDialog?,
    actions: CategoryListActions,
) {
    when (dialog) {
        null -> Unit

        is NamePrompt -> NamePromptDialog(
            prompt = dialog,
            onSubmit = actions.onSubmitName,
            onDismiss = actions.onDismissDialog,
        )

        is RecolorPrompt -> RecolorDialog(
            prompt = dialog,
            onPick = actions.onRecolor,
            onDismiss = actions.onDismissDialog,
        )

        is ConfirmDeletePrompt -> ConfirmDialog(
            title = stringResource(R.string.categories_delete_title, dialog.name),
            body = stringResource(R.string.categories_delete_body),
            // Null rather than zero: delete is offered only where the count is zero, and
            // "0 transactions affected" reads as a warning where there is nothing to warn.
            affectedCount = null,
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDialog,
        )

        is ChooseMergeTargetPrompt -> MergeTargetDialog(
            prompt = dialog,
            onChoose = actions.onChooseMergeTarget,
            onDismiss = actions.onDismissDialog,
        )

        is ConfirmMergePrompt -> ConfirmDialog(
            title = stringResource(
                R.string.categories_merge_title,
                dialog.fromName,
                dialog.toName,
            ),
            body = stringResource(
                R.string.categories_merge_body,
                dialog.toName,
                dialog.fromName,
            ),
            affectedCount = dialog.affectedCount,
            confirmLabel = stringResource(R.string.categories_action_merge_label),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = actions.onConfirmMerge,
            onDismiss = actions.onDismissDialog,
        )
    }
}

@Composable
private fun NamePromptDialog(
    prompt: NamePrompt,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = when (prompt) {
        is RenameCategoryPrompt -> prompt.currentName
        else -> ""
    }
    // Keyed on the prompt's identity rather than remembered flat: a rejected name has to
    // stay in the field so the user can edit it instead of retyping it.
    var name by rememberSaveable(initial) { mutableStateOf(initial) }

    val title = when (prompt) {
        is CreateCategoryPrompt -> stringResource(R.string.categories_new_category)
        is RenameCategoryPrompt -> stringResource(
            R.string.categories_rename_title,
            prompt.currentName,
        )
    }
    val error = when (val problem = prompt.error) {
        null -> null
        NameError.Blank -> stringResource(R.string.categories_error_blank)
        is NameError.Duplicate -> stringResource(
            R.string.categories_error_duplicate,
            problem.name,
        )
    }
    val confirmLabel = when (prompt) {
        is CreateCategoryPrompt -> R.string.action_create
        else -> R.string.action_save
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = SakuTheme.typography.title, color = SakuTheme.colors.ink)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = error != null,
                    label = { Text(stringResource(R.string.entry_name_hint)) },
                    supportingText = error?.let {
                        {
                            Text(
                                text = it,
                                style = SakuTheme.typography.caption,
                                color = SakuTheme.colors.expense,
                            )
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name) }) {
                Text(
                    text = stringResource(confirmLabel),
                    color = SakuTheme.colors.accent,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = SakuTheme.colors.ink2,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        containerColor = SakuTheme.colors.surface,
    )
}

@Composable
private fun RecolorDialog(
    prompt: RecolorPrompt,
    onPick: (ColorSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val slots = remember { List(ColorSlot.COUNT) { ColorSlot(it) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.categories_recolour_title, prompt.name),
                style = SakuTheme.typography.title,
                color = colors.ink,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.selectableGroup(),
            ) {
                // Four to a row. Eight 48dp targets side by side overflow a dialog on a
                // narrow phone, and shrinking them below 48dp is not an option.
                slots.chunked(4).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        chunk.forEach { slot ->
                            val label = stringResource(
                                R.string.categories_colour_slot,
                                slot.index + 1,
                            )
                            val selected = slot.index == prompt.current.index
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onPick(slot) },
                                    )
                                    .semantics { contentDescription = label },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (selected) 34.dp else 28.dp)
                                        .clip(CircleShape)
                                        .background(colors.forSlot(slot))
                                        .then(
                                            // Selection is a ring, not a colour change:
                                            // the swatch IS the colour, so it cannot also
                                            // signal state by changing hue.
                                            if (selected) {
                                                Modifier.border(2.dp, colors.ink, CircleShape)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_done),
                    color = colors.accent,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        containerColor = colors.surface,
    )
}

@Composable
private fun MergeTargetDialog(
    prompt: ChooseMergeTargetPrompt,
    onChoose: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.categories_merge_choose_title, prompt.fromName),
                style = SakuTheme.typography.title,
                color = SakuTheme.colors.ink,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                // The picker already filters archived rows out; the targets it is handed
                // are same-kind and exclude the source, which is the rule that matters.
                CategoryPicker(
                    categories = prompt.targets.map { it.category },
                    onSelect = { onChoose(it.id) },
                    onCreateNew = null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = SakuTheme.colors.ink2,
                    style = SakuTheme.typography.bodyStrong,
                )
            }
        },
        containerColor = SakuTheme.colors.surface,
    )
}
