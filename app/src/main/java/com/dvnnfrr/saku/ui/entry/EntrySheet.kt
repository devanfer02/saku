package com.dvnnfrr.saku.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.AccountPicker
import com.dvnnfrr.saku.ui.shared.CategoryPicker
import com.dvnnfrr.saku.ui.shared.ConfirmDialog
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.shared.SubcategoryPicker
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forDirection
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** Which field's picker is expanded. Only one at a time; the sheet is not tall. */
private enum class OpenPicker { NONE, ACCOUNT, DESTINATION, CATEGORY, SUBCATEGORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    editingId: Long? = null,
    viewModel: TransactionEntryViewModel = viewModel(factory = TransactionEntryViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(editingId) { viewModel.open(editingId) }
    LaunchedEffect(state.saved) { if (state.saved) onDismiss() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SakuTheme.colors.surface,
        modifier = modifier,
    ) {
        EntrySheetBody(state = state, viewModel = viewModel)
    }
}

@Composable
private fun EntrySheetBody(
    state: EntryUiState,
    viewModel: TransactionEntryViewModel,
) {
    val form = state.form
    val spacing = SakuTheme.spacing
    var openPicker by rememberSaveable { mutableStateOf(OpenPicker.NONE) }
    var creating by rememberSaveable { mutableStateOf<OpenPicker?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm)) {
        // The fields scroll and the keypad is pinned. With a picker expanded the
        // content exceeded the sheet height and the Save row was pushed off with no
        // way to reach it.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
        Header(state, onDelete = { confirmingDelete = true })

        SegmentedTabs(
            options = ENTRY_TYPES.map { stringResource(it.entryLabelRes()) },
            selectedIndex = ENTRY_TYPES.indexOf(form.type).coerceAtLeast(0),
            onSelect = {
                // Collapse the open picker: switching to Transfer replaces the
                // Category row, leaving an orphaned CategoryPicker whose create
                // action could no longer do anything.
                openPicker = OpenPicker.NONE
                viewModel.setType(ENTRY_TYPES[it])
            },
            modifier = Modifier.padding(horizontal = spacing.screen),
        )

        AmountDisplay(state)

        Column(modifier = Modifier.padding(horizontal = spacing.screen)) {
            FieldRow(
                label = stringResource(R.string.entry_field_when),
                value = form.occurredAt.whenLabel(),
                problem = null,
                onClick = { pickingDate = true },
            )

            FieldRow(
                label = stringResource(R.string.entry_field_account),
                value = state.accounts.firstOrNull { it.id == form.accountId }?.name,
                problem = state.problemFor(EntryField.ACCOUNT),
                onClick = { openPicker = openPicker.toggle(OpenPicker.ACCOUNT) },
            )

            when (form.type) {
                TxnType.TRANSFER -> FieldRow(
                    label = stringResource(R.string.entry_field_destination),
                    value = state.accounts.firstOrNull { it.id == form.toAccountId }?.name,
                    problem = state.problemFor(EntryField.DESTINATION),
                    onClick = { openPicker = openPicker.toggle(OpenPicker.DESTINATION) },
                )

                else -> {
                    FieldRow(
                        label = stringResource(R.string.entry_field_category),
                        value = state.categories.firstOrNull { it.id == form.categoryId }?.name,
                        problem = state.problemFor(EntryField.CATEGORY),
                        onClick = { openPicker = openPicker.toggle(OpenPicker.CATEGORY) },
                    )
                    // Only once a category exists: a subcategory belongs to one, and
                    // offering the field before then invites a selection with no home.
                    if (form.categoryId != null) {
                        FieldRow(
                            label = stringResource(R.string.entry_field_subcategory),
                            value = state.subcategories
                                .firstOrNull { it.id == form.subcategoryId }?.name,
                            unsetLabel = stringResource(R.string.entry_field_none),
                            problem = null,
                            onClick = { openPicker = openPicker.toggle(OpenPicker.SUBCATEGORY) },
                        )
                    }
                }
            }

            NoteField(value = form.note, onChange = viewModel::setNote)
        }

        ExpandedPicker(
            open = openPicker,
            state = state,
            viewModel = viewModel,
            onClose = { openPicker = OpenPicker.NONE },
            onCreate = { creating = openPicker },
        )
        }

        Keypad(
            onDigit = viewModel::onDigit,
            onTripleZero = viewModel::onTripleZero,
            onBackspace = viewModel::onBackspace,
            onOperator = viewModel::onOperator,
            onSave = viewModel::save,
        )
    }

    creating?.let { which ->
        NameDialog(
            titleRes = if (which == OpenPicker.CATEGORY) {
                R.string.entry_new_category
            } else {
                R.string.entry_new_subcategory
            },
            onConfirm = { name ->
                if (which == OpenPicker.CATEGORY) {
                    viewModel.createCategory(name)
                } else {
                    viewModel.createSubcategory(name)
                }
                creating = null
                openPicker = OpenPicker.NONE
            },
            onDismiss = { creating = null },
        )
    }

    if (pickingDate) {
        DatePickerSheet(
            initial = form.occurredAt,
            onPick = { date ->
                viewModel.setOccurredAt(
                    LocalDateTime(date, form.occurredAt.time),
                )
                // Chain straight into the time picker: a back-dated entry almost
                // always needs its time corrected too, and two taps beats two trips.
                pickingTime = true
            },
            onDismiss = { pickingDate = false },
        )
    }

    if (pickingTime) {
        TimePickerSheet(
            initial = form.occurredAt,
            onPick = { hour, minute ->
                viewModel.setOccurredAt(
                    LocalDateTime(form.occurredAt.date, LocalTime(hour, minute)),
                )
            },
            onDismiss = { pickingTime = false },
        )
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = stringResource(R.string.entry_delete_title),
            body = stringResource(R.string.entry_delete_body),
            affectedCount = null,
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    state.duplicateName?.let { name ->
        ConfirmDialog(
            title = stringResource(R.string.entry_duplicate_notice, name),
            body = "",
            affectedCount = null,
            onConfirm = viewModel::dismissDuplicateNotice,
            onDismiss = viewModel::dismissDuplicateNotice,
        )
    }
}

private fun OpenPicker.toggle(target: OpenPicker): OpenPicker =
    if (this == target) OpenPicker.NONE else target

@Composable
private fun Header(state: EntryUiState, onDelete: () -> Unit) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (state.form.isEditing) R.string.entry_title_edit else R.string.entry_title_new,
            ),
            style = SakuTheme.typography.heading,
            color = SakuTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (state.form.isEditing) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = SakuTheme.colors.expense,
                )
            }
        }
    }
}

@Composable
private fun AmountDisplay(state: EntryUiState) {
    val form = state.form
    val spacing = SakuTheme.spacing
    val problem = state.problemFor(EntryField.AMOUNT)

    Column(
        modifier = Modifier.fillMaxWidth().padding(spacing.screen),
        horizontalAlignment = Alignment.End,
    ) {
        // Mid-expression the committed half is shown above the operand being typed,
        // so the user can see what they are adding to.
        if (form.amount.hasPendingExpression) {
            Text(
                text = buildString {
                    append(formatIdr(com.dvnnfrr.saku.domain.Money(form.amount.accumulated), false))
                    append(if (form.amount.pendingOperator == AmountOperator.PLUS) " + " else " − ")
                },
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink3,
            )
        }
        // The running total, not the operand being typed. Showing the operand meant
        // typing 16000 then tapping + displayed Rp 0 while Save would have recorded
        // 16.000 — and the "must be more than zero" message pointed at a figure that
        // was plainly not zero.
        Text(
            text = formatIdr(form.amount.value, withPrefix = true),
            style = SakuTheme.typography.amountLarge,
            color = SakuTheme.colors.forDirection(form.type),
        )
        problem?.let { Problem(it) }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String?,
    problem: EntryProblem?,
    onClick: () -> Unit,
    unsetLabel: String = stringResource(R.string.entry_field_unset),
) {
    val spacing = SakuTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Text(
                text = value ?: unsetLabel,
                style = SakuTheme.typography.body,
                color = if (value == null) SakuTheme.colors.ink3 else SakuTheme.colors.ink,
            )
        }
        problem?.let { Problem(it) }
    }
}

@Composable
private fun Problem(problem: EntryProblem) {
    Text(
        text = stringResource(problem.messageRes()),
        style = SakuTheme.typography.caption,
        color = SakuTheme.colors.expense,
        modifier = Modifier.padding(bottom = SakuTheme.spacing.xs),
    )
}

@Composable
private fun NoteField(value: String, onChange: (String) -> Unit) {
    val spacing = SakuTheme.spacing
    val label = stringResource(R.string.entry_field_note)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = SakuTheme.colors.ink,
                textAlign = TextAlign.End,
                fontSize = SakuTheme.typography.body.fontSize,
            ),
            // The "Note" Text is a sibling, so without this TalkBack focuses the
            // field and announces only "edit box".
            modifier = Modifier
                .weight(2f)
                .padding(vertical = spacing.sm)
                .semantics { contentDescription = label },
        )
    }
}

@Composable
private fun ExpandedPicker(
    open: OpenPicker,
    state: EntryUiState,
    viewModel: TransactionEntryViewModel,
    onClose: () -> Unit,
    onCreate: () -> Unit,
) {
    if (open == OpenPicker.NONE) return
    val maxHeight = 260.dp

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        when (open) {
            // No create action on either account picker. Accounts are made on the
            // Accounts screen; wiring this to the shared NameDialog created a
            // *subcategory* named after the account the user thought they were adding.
            OpenPicker.ACCOUNT -> AccountPicker(
                accounts = state.accounts,
                selectedId = state.form.accountId,
                onSelect = { viewModel.setAccount(it.id); onClose() },
                onCreateNew = null,
            )

            OpenPicker.DESTINATION -> AccountPicker(
                accounts = state.accounts.filterNot { it.id == state.form.accountId },
                selectedId = state.form.toAccountId,
                onSelect = { viewModel.setDestination(it.id); onClose() },
                onCreateNew = null,
            )

            OpenPicker.CATEGORY -> CategoryPicker(
                categories = state.categories,
                selectedId = state.form.categoryId,
                onSelect = { viewModel.selectCategory(it.id); onClose() },
                onCreateNew = onCreate,
            )

            OpenPicker.SUBCATEGORY -> SubcategoryPicker(
                subcategories = state.subcategories,
                selectedId = state.form.subcategoryId,
                onSelect = { viewModel.selectSubcategory(it.id); onClose() },
                onCreateNew = onCreate,
            )

            OpenPicker.NONE -> Unit
        }
    }
}

@Composable
private fun NameDialog(
    @androidx.annotation.StringRes titleRes: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.entry_name_hint)) },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
