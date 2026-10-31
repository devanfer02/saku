package com.dvnnfrr.saku.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.Tag
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme

/**
 * The entry sheet's Tags row: one chip per tag, each removing itself on tap, then Add tag.
 * Optional, so it never shows a problem and Save never waits on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsRow(
    tags: List<EntryTag>,
    onRemove: (EntryTag) -> Unit,
    /** Null hides Add tag, for a host that shows the picker already (the Receipt screen). */
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.entry_field_tags),
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        FlowRow(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
        ) {
            tags.forEach { tag -> TagChip(tag, onRemove = { onRemove(tag) }) }
            if (onAdd != null) {
                AssistChip(
                    onClick = onAdd,
                    label = { Text(stringResource(R.string.entry_tag_add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        }
    }
}

/**
 * A tag on the row. The whole chip removes it, with the cross as the visual cue; TalkBack
 * hears one node, "Tag Trip Bali, remove", instead of the name and an unlabelled icon.
 */
@Composable
private fun TagChip(tag: EntryTag, onRemove: () -> Unit) {
    val spoken = stringResource(R.string.entry_tag_remove, tag.name)
    InputChip(
        selected = false,
        onClick = onRemove,
        label = {
            Text(
                text = tag.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = TAG_CHIP_MAX_WIDTH),
            )
        },
        trailingIcon = {
            Icon(Icons.Filled.Close, contentDescription = null)
        },
        colors = InputChipDefaults.inputChipColors(labelColor = SakuTheme.colors.ink),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
            onClick { onRemove(); true }
        },
    )
}

/**
 * Add tag, in the keypad's place: the name field on top, matching tags below it, and Done
 * last, in the lower half where the thumb already is. Keyboard Done adds what is typed and
 * keeps the picker open for another; the Done button adds it and closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPicker(
    query: String,
    picker: TagPickerUi,
    hasAnyTag: Boolean,
    onQuery: (String) -> Unit,
    onPick: (Tag) -> Unit,
    onAddTyped: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.entry_tag_name_label)) },
            placeholder = { Text(stringResource(R.string.entry_tag_name_example)) },
            isError = picker.problem != null,
            supportingText = picker.problem?.let { problem -> { Text(stringResource(problem.messageRes())) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onAddTyped() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = TAG_LIST_MAX_HEIGHT)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            picker.createName?.let { name ->
                AssistChip(
                    onClick = onAddTyped,
                    label = { Text(stringResource(R.string.entry_tag_create, name)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
            picker.matches.forEach { tag ->
                SuggestionChip(onClick = { onPick(tag) }, label = { Text(tag.name) })
            }
        }
        if (!hasAnyTag && picker.createName == null) {
            Text(
                text = stringResource(R.string.entry_tag_none),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
            )
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
            shape = RoundedCornerShape(spacing.corner),
            // onPrimary as on the keypad's Save: white on the dark accent fails contrast.
            colors = ButtonDefaults.buttonColors(
                containerColor = SakuTheme.colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            // Done also adds what is typed, so say so: "mon" would otherwise become a tag by surprise.
            val typed = query.trim()
            val label = if (typed.isEmpty()) {
                stringResource(R.string.entry_tag_done)
            } else {
                stringResource(R.string.entry_tag_add_typed, typed)
            }
            Text(label, style = SakuTheme.typography.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun TagNameProblem.messageRes(): Int = when (this) {
    TagNameProblem.TOO_LONG -> R.string.entry_tag_too_long
    TagNameProblem.HAS_SEPARATOR -> R.string.entry_tag_has_separator
}

/** About four rows of chips; a longer list scrolls inside the picker. */
private val TAG_LIST_MAX_HEIGHT = 168.dp

/** Long names are cut so two chips still fit beside the label. */
private val TAG_CHIP_MAX_WIDTH = 160.dp

@SakuPreview
@Composable
private fun TagsRowPreview() {
    SakuPreviewSurface {
        Column(modifier = Modifier.width(360.dp)) {
            TagsRow(
                tags = listOf(EntryTag(1, "Monthly grocery"), EntryTag(null, "Trip Bali")),
                onRemove = {},
                onAdd = {},
            )
            TagPicker(
                query = "gro",
                picker = TagPickerUi(
                    matches = listOf(Tag(1, "Monthly grocery", 0, false), Tag(2, "Groceries Ramadan", 1, false)),
                    createName = "gro",
                ),
                hasAnyTag = true,
                onQuery = {},
                onPick = {},
                onAddTyped = {},
                onDone = {},
            )
        }
    }
}
