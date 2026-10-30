package com.dvnnfrr.saku.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.entry.DatePickerSheet
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forSlot
import com.dvnnfrr.saku.ui.shared.MoneyText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Create and edit are the same four fields, so they are the same sheet.
 *
 * The opening balance and its as-of date are here rather than behind a separate flow
 * because first-run setup seeds every account at zero and then needs somewhere to put
 * the real figures. Saving the date can be refused — moving a cutover past a transfer
 * would count that transfer on one leg and not the other — and the refusal arrives as a
 * message rather than as a crash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorSheet(
    form: AccountFormState,
    onDismiss: () -> Unit,
    onSubmit: (AccountFormState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(form.id) { mutableStateOf(form) }
    var showProblem by remember(form.id) { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val spacing = SakuTheme.spacing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SakuTheme.colors.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screen, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(
                    if (draft.isEditing) {
                        R.string.accounts_edit_title
                    } else {
                        R.string.accounts_new_title
                    },
                ),
                style = SakuTheme.typography.heading,
                color = SakuTheme.colors.ink,
            )

            NameField(
                value = draft.name,
                onChange = { draft = draft.copy(name = it) },
                // Only after a save attempt. Marking an untouched field red the moment
                // the sheet opens says something is wrong when nothing is yet.
                problem = stringResource(R.string.accounts_error_name_missing)
                    .takeIf { showProblem && draft.nameIsBlank },
            )

            FieldLabel(stringResource(R.string.accounts_field_type))
            SegmentedTabs(
                options = ACCOUNT_TYPES.map { stringResource(it.labelRes()) },
                selectedIndex = ACCOUNT_TYPES.indexOf(draft.type),
                onSelect = { draft = draft.copy(type = ACCOUNT_TYPES[it]) },
            )

            FieldLabel(stringResource(R.string.accounts_field_colour))
            ColorSlotPicker(
                selected = draft.colorSlot,
                onSelect = { draft = draft.copy(colorSlot = it) },
            )

            AmountField(
                label = stringResource(R.string.accounts_field_opening_balance),
                value = draft.openingBalance,
                onChange = { draft = draft.copy(openingBalance = it) },
            )

            DateField(
                label = stringResource(R.string.accounts_field_opening_as_of),
                value = draft.openingAsOf,
                onClick = { pickingDate = true },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.lg),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = SakuTheme.colors.ink2,
                        style = SakuTheme.typography.bodyStrong,
                    )
                }
                TextButton(
                    onClick = {
                        if (draft.nameIsBlank) showProblem = true else onSubmit(draft)
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (draft.isEditing) R.string.action_save else R.string.action_create,
                        ),
                        color = SakuTheme.colors.accent,
                        style = SakuTheme.typography.bodyStrong,
                    )
                }
            }
        }
    }

    if (pickingDate) {
        DatePickerSheet(
            initial = LocalDateTime(draft.openingAsOf, LocalTime(0, 0)),
            onPick = { draft = draft.copy(openingAsOf = it) },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = SakuTheme.typography.label, color = SakuTheme.colors.ink2)
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, problem: String?) {
    val spacing = SakuTheme.spacing
    val label = stringResource(R.string.accounts_field_name)
    Column {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = SakuTheme.colors.ink,
                fontSize = SakuTheme.typography.body.fontSize,
            ),
            // The label lives inside the field rather than above it. A sibling Text
            // saying the same word is a second thing on screen reading "Name" that does
            // nothing when tapped, and it is the one an end-to-end driver finds first.
            decorationBox = { field -> Placeholder(label, value.isEmpty(), field) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.touchTarget)
                .padding(vertical = spacing.sm)
                .semantics { contentDescription = label },
        )
        problem?.let {
            Text(text = it, style = SakuTheme.typography.caption, color = SakuTheme.colors.expense)
        }
    }
}

/** An empty field still has to say what it is for, and still has to be tappable. */
@Composable
private fun Placeholder(
    label: String,
    isEmpty: Boolean,
    field: @Composable () -> Unit,
    align: Alignment = Alignment.CenterStart,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (isEmpty) {
            Text(
                text = label,
                style = SakuTheme.typography.body,
                color = SakuTheme.colors.ink2,
                modifier = Modifier.align(align),
            )
        }
        field()
    }
}

/**
 * Whole rupiah, typed as digits with an optional leading minus.
 *
 * The minus is not decoration: a liability's opening balance is negative while money is
 * owed, and without a way to type one, a card could only ever be opened in credit.
 */
@Composable
private fun AmountField(label: String, value: Money, onChange: (Money) -> Unit) {
    val spacing = SakuTheme.spacing
    var text by remember { mutableStateOf(if (value.isZero) "" else value.rupiah.toString()) }

    Column {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val negative = raw.startsWith('-')
                val digits = raw.filter { it.isDigit() }.take(MAX_RUPIAH_DIGITS)
                text = if (negative) "-$digits" else digits
                onChange(Money((if (negative) "-$digits" else digits).toLongOrNull() ?: 0L))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                color = SakuTheme.colors.ink,
                textAlign = TextAlign.End,
                fontSize = SakuTheme.typography.amountMedium.fontSize,
            ),
            decorationBox = { field ->
                Placeholder(label, text.isEmpty(), field, align = Alignment.CenterStart)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.touchTarget)
                .padding(vertical = spacing.sm)
                .semantics { contentDescription = label },
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            MoneyText(
                money = value,
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
            )
        }
    }
}

/** Long is 19 digits; 15 keeps the largest openable balance at 999 trillion rupiah. */
private const val MAX_RUPIAH_DIGITS = 15

@Composable
private fun DateField(label: String, value: LocalDate, onClick: () -> Unit) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = spacing.touchTarget)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Text(text = value.label(), style = SakuTheme.typography.body, color = SakuTheme.colors.ink)
    }
}

/**
 * The eight palette slots.
 *
 * Colour is identity here, not decoration — a slot is pinned to the account for life so
 * that re-sorting the list never repaints the chart. Each swatch is named, so the choice
 * is not itself made by colour alone.
 */
@Composable
private fun ColorSlotPicker(selected: ColorSlot?, onSelect: (ColorSlot) -> Unit) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        COLOR_SLOTS.forEach { slot ->
            val isSelected = slot == selected
            val description = stringResource(R.string.accounts_colour_slot, slot.index + 1)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = spacing.touchTarget)
                    .clickable { onSelect(slot) }
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = description
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(spacing.xl)
                        .background(SakuTheme.colors.forSlot(slot), CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = spacing.xs / 2,
                                    color = SakuTheme.colors.ink,
                                    shape = CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@SakuPreview
@Composable
private fun ColorSlotPickerPreview() {
    SakuPreviewSurface {
        Column {
            ColorSlotPicker(selected = ColorSlot(2), onSelect = {})
        }
    }
}
