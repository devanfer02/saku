package com.dvnnfrr.saku.ui.firstrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.accounts.ACCOUNT_TYPES
import com.dvnnfrr.saku.ui.accounts.labelRes
import com.dvnnfrr.saku.ui.importexport.PrimaryButton
import com.dvnnfrr.saku.ui.shared.ConfirmDialog
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.ScreenHeader
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.transactions.dateLabel
import kotlinx.datetime.LocalDate

/**
 * Set up manually: the accounts step, then the categories step, then one Done.
 *
 * System back walks the steps: from categories to accounts, and from accounts out to the
 * welcome screen through [onBack]. Nothing is written until Done.
 */
@Composable
fun FirstRunSetupScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FirstRunSetupViewModel = viewModel(factory = FirstRunSetupViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val back = { if (!viewModel.back()) onBack() }

    BackHandler(onBack = back)

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    FirstRunSetupScreen(
        state = state,
        modifier = modifier,
        onBack = back,
        onRename = viewModel::rename,
        onRetype = viewModel::retype,
        onAmountChange = viewModel::setAmount,
        onAddAccount = viewModel::addAccount,
        onRemoveAccount = viewModel::removeAccount,
        onNext = viewModel::next,
        onToggleCategory = viewModel::toggleCategory,
        onAddCategory = viewModel::addCategory,
        onDone = viewModel::done,
        onDismissRefusal = viewModel::dismissRefusal,
    )
}

@Composable
fun FirstRunSetupScreen(
    state: FirstRunSetupUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRename: (key: String, name: String) -> Unit = { _, _ -> },
    onRetype: (key: String, type: AccountType) -> Unit = { _, _ -> },
    onAmountChange: (key: String, raw: String) -> Unit = { _, _ -> },
    onAddAccount: () -> Unit = {},
    onRemoveAccount: (key: String) -> Unit = {},
    onNext: () -> Unit = {},
    onToggleCategory: (CategoryChoice) -> Unit = {},
    onAddCategory: (name: String, kind: CategoryKind) -> Unit = { _, _ -> },
    onDone: () -> Unit = {},
    onDismissRefusal: () -> Unit = {},
) {
    val spacing = SakuTheme.spacing
    val onAccounts = state.step == SetupStep.ACCOUNTS

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(
                if (onAccounts) R.string.setup_accounts_title else R.string.setup_categories_title,
            ),
            onBack = onBack,
            backLabel = stringResource(R.string.screen_back),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (onAccounts) {
                accountsStep(state, onRename, onRetype, onAmountChange, onAddAccount, onRemoveAccount)
            } else {
                categoriesStep(state, onToggleCategory, onAddCategory)
            }
            item(key = "end") { Spacer(Modifier.heightIn(min = spacing.lg)) }
        }

        PrimaryButton(
            label = when {
                state.saving -> stringResource(R.string.setup_saving)
                onAccounts -> stringResource(R.string.setup_next)
                else -> stringResource(R.string.action_done)
            },
            onClick = if (onAccounts) onNext else onDone,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen, vertical = spacing.md)
                .heightIn(min = spacing.touchTarget),
        )
    }

    state.refusal?.let { reason ->
        ConfirmDialog(
            title = stringResource(R.string.setup_refused_title),
            // The repository's own words: they name the account and the rule.
            body = reason,
            affectedCount = null,
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = onDismissRefusal,
            onDismiss = onDismissRefusal,
        )
    }
}

// ---- Accounts step -------------------------------------------------------------------

private fun LazyListScope.accountsStep(
    state: FirstRunSetupUiState,
    onRename: (String, String) -> Unit,
    onRetype: (String, AccountType) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
) {
    item(key = "intro") {
        Intro(stringResource(R.string.setup_accounts_intro, state.today.dateLabel()))
    }
    items(state.accounts, key = { it.draft.key }) { row ->
        AccountCard(
            row = row,
            onRename = { onRename(row.draft.key, it) },
            onRetype = { onRetype(row.draft.key, it) },
            onAmountChange = { onAmountChange(row.draft.key, it) },
            onRemove = { onRemoveAccount(row.draft.key) },
        )
    }
    item(key = "add") {
        TextButton(
            onClick = onAddAccount,
            modifier = Modifier
                .padding(horizontal = SakuTheme.spacing.sm)
                .heightIn(min = SakuTheme.spacing.touchTarget),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = SakuTheme.colors.accent)
            Spacer(Modifier.width(SakuTheme.spacing.sm))
            Text(
                text = stringResource(R.string.setup_add_account),
                style = SakuTheme.typography.bodyStrong,
                color = SakuTheme.colors.accent,
            )
        }
    }
}

@Composable
private fun Intro(text: String) {
    Text(
        text = text,
        style = SakuTheme.typography.body,
        color = SakuTheme.colors.ink2,
        modifier = Modifier.padding(horizontal = SakuTheme.spacing.screen),
    )
}

/** One account: its name, its type, and what is in it today. */
@Composable
private fun AccountCard(
    row: AccountDraftUi,
    onRename: (String) -> Unit,
    onRetype: (AccountType) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val draft = row.draft
    val problem = when (row.problem) {
        DraftProblem.NAME_MISSING -> stringResource(R.string.accounts_error_name_missing)
        DraftProblem.NAME_TAKEN -> stringResource(R.string.setup_error_name_taken)
        null -> null
    }

    Column(
        modifier = Modifier
            .padding(horizontal = spacing.screen)
            .fillMaxWidth()
            .clip(RoundedCornerShape(spacing.corner))
            .background(colors.surface)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(
                value = draft.name,
                onChange = onRename,
                label = stringResource(R.string.accounts_field_name),
                style = SakuTheme.typography.bodyStrong,
                problem = problem,
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.weight(1f),
            )
            if (draft.isAdded) {
                val removeLabel = if (draft.name.isBlank()) {
                    stringResource(R.string.setup_remove_unnamed_account)
                } else {
                    stringResource(R.string.setup_remove_account, draft.name.trim())
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = removeLabel, tint = colors.ink2)
                }
            }
        }
        problem?.let {
            Text(text = it, style = SakuTheme.typography.caption, color = colors.expense)
        }

        SegmentedTabs(
            options = ACCOUNT_TYPES.map { stringResource(it.labelRes()) },
            selectedIndex = ACCOUNT_TYPES.indexOf(draft.type),
            onSelect = { onRetype(ACCOUNT_TYPES[it]) },
        )

        AmountInputField(
            digits = draft.amountDigits,
            amount = draft.amount,
            label = stringResource(
                if (draft.type == AccountType.LIABILITY) {
                    R.string.setup_field_owed_today
                } else {
                    R.string.setup_field_in_it_today
                },
            ),
            onChange = onAmountChange,
        )
    }
}

/**
 * Whole rupiah as digits, with the formatted figure beneath it in [MoneyText], as the
 * account editor does. The sign comes from the type, so there is no minus to type.
 */
@Composable
private fun AmountInputField(digits: String, amount: Money, label: String, onChange: (String) -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SakuTheme.typography.body,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = digits,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                textStyle = SakuTheme.typography.amountMedium.copy(color = colors.ink, textAlign = TextAlign.End),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                        if (digits.isEmpty()) {
                            Text("0", style = SakuTheme.typography.amountMedium, color = colors.ink2)
                        }
                        field()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.touchTarget)
                    .semantics { contentDescription = label },
            )
            MoneyText(money = amount, style = SakuTheme.typography.caption, color = colors.ink2)
        }
    }
}

/** A single-line field whose label sits inside it while empty, as in the account editor. */
@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SakuTheme.typography.body,
    problem: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    val colors = SakuTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                if (value.isEmpty()) Text(label, style = style, color = colors.ink2)
                field()
            }
        },
        modifier = modifier
            .heightIn(min = SakuTheme.spacing.touchTarget)
            .semantics {
                contentDescription = label
                if (problem != null) error(problem)
            },
    )
}

// ---- Categories step -----------------------------------------------------------------

private fun LazyListScope.categoriesStep(
    state: FirstRunSetupUiState,
    onToggle: (CategoryChoice) -> Unit,
    onAdd: (String, CategoryKind) -> Unit,
) {
    item(key = "intro") { Intro(stringResource(R.string.setup_categories_intro)) }
    item(key = "expense") {
        CategoryGroup(
            heading = stringResource(R.string.categories_tab_expense),
            newLabel = stringResource(R.string.setup_category_new_expense),
            options = state.expense,
            onToggle = onToggle,
            onAdd = { onAdd(it, CategoryKind.EXPENSE) },
        )
    }
    item(key = "income") {
        CategoryGroup(
            heading = stringResource(R.string.categories_tab_income),
            newLabel = stringResource(R.string.setup_category_new_income),
            options = state.income,
            onToggle = onToggle,
            onAdd = { onAdd(it, CategoryKind.INCOME) },
        )
    }
}

@Composable
private fun CategoryGroup(
    heading: String,
    newLabel: String,
    options: List<CategoryOptionUi>,
    onToggle: (CategoryChoice) -> Unit,
    onAdd: (String) -> Unit,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    var typed by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(typed)
        typed = ""
    }

    Column(modifier = Modifier.padding(horizontal = spacing.screen)) {
        Text(
            text = heading,
            style = SakuTheme.typography.heading,
            color = colors.ink,
            modifier = Modifier
                .padding(top = spacing.md, bottom = spacing.sm)
                .semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(spacing.corner))
                .background(colors.surface),
        ) {
            options.forEach { option ->
                TickRow(option = option, onToggle = { onToggle(option.choice) })
                HorizontalDivider(color = colors.line)
            }
            Row(
                modifier = Modifier.padding(start = spacing.lg, end = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextInput(
                    value = typed,
                    onChange = { typed = it },
                    label = newLabel,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                    onImeAction = submit,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = submit,
                    enabled = typed.isNotBlank(),
                    modifier = Modifier.heightIn(min = spacing.touchTarget),
                ) {
                    Text(
                        text = stringResource(R.string.setup_category_add),
                        style = SakuTheme.typography.bodyStrong,
                        color = if (typed.isNotBlank()) colors.accent else colors.inkDisabled,
                    )
                }
            }
        }
    }
}

/** A whole-row checkbox. One that already exists is shown ticked and cannot be unticked. */
@Composable
private fun TickRow(option: CategoryOptionUi, onToggle: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.touchTarget)
            .toggleable(
                value = option.ticked,
                enabled = !option.exists,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = option.ticked,
            onCheckedChange = null,
            enabled = !option.exists,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.ink2,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                disabledCheckedColor = colors.inkDisabled,
            ),
            modifier = Modifier.padding(spacing.sm),
        )
        Text(
            text = option.choice.name,
            style = SakuTheme.typography.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (option.exists) {
            Text(
                text = stringResource(R.string.setup_category_exists),
                style = SakuTheme.typography.caption,
                color = colors.ink2,
                modifier = Modifier.padding(end = spacing.sm),
            )
        }
    }
}

// ---- Previews ------------------------------------------------------------------------

private val PREVIEW_TODAY = LocalDate(2026, 10, 30)

private val PREVIEW_ACCOUNTS = FirstRunSetupUiState(
    today = PREVIEW_TODAY,
    accounts = listOf(
        AccountDraftUi(AccountDraft(existingKey(1), 1, "Cash", AccountType.CASH, "250000"), null),
        AccountDraftUi(AccountDraft(existingKey(2), 2, "Card", AccountType.BANK, "4100000"), null),
        AccountDraftUi(AccountDraft(addedKey(0), null, "Kartu Kredit", AccountType.LIABILITY, "750000"), null),
        AccountDraftUi(AccountDraft(addedKey(1), null, "", AccountType.BANK), DraftProblem.NAME_MISSING),
    ),
)

private val PREVIEW_PICKS = CategoryPicks()
    .toggle(CategoryChoice("Food", CategoryKind.EXPENSE))
    .toggle(CategoryChoice("Transport", CategoryKind.EXPENSE))
    .add("Freelance", CategoryKind.INCOME)

private val PREVIEW_CATEGORIES = FirstRunSetupUiState(
    step = SetupStep.CATEGORIES,
    today = PREVIEW_TODAY,
    expense = categoryOptions(PREVIEW_PICKS, CategoryKind.EXPENSE, emptyList()),
    income = categoryOptions(PREVIEW_PICKS, CategoryKind.INCOME, emptyList()),
)

@SakuPreview
@Composable
private fun SetupAccountsStepPreview() {
    SakuPreviewSurface(padded = false) { FirstRunSetupScreen(state = PREVIEW_ACCOUNTS) }
}

@SakuPreview
@Composable
private fun SetupCategoriesStepPreview() {
    SakuPreviewSurface(padded = false) { FirstRunSetupScreen(state = PREVIEW_CATEGORIES) }
}

@SakuPreview
@Composable
private fun SetupSavingPreview() {
    SakuPreviewSurface(padded = false) {
        FirstRunSetupScreen(state = PREVIEW_CATEGORIES.copy(saving = true))
    }
}
