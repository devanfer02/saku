package com.dvnnfrr.saku.ui.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.ColorSwatch
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.shared.ShareRing
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import kotlinx.datetime.LocalDate

/**
 * The filter sheet, opened over Trans or Stats for [range], the period that screen shows.
 *
 * One ViewModel per range. Every tick applies at once through [SharedFilter], so closing
 * the sheet, however it is closed, keeps what was ticked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    range: DateRange,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilterSheetViewModel = viewModel(
        key = "filter-sheet-$range",
        factory = FilterSheetViewModel.factory(range),
    ),
) {
    val state by viewModel.state.collectAsState()

    // The ViewModel outlives a closed sheet, so its tab would carry over to the next
    // opening. Reset once per opening, not on every recomposition or rotation.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            viewModel.selectTab(FilterTab.INCOME)
            opened = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SakuTheme.colors.surface,
        modifier = modifier,
    ) {
        FilterSheet(
            state = state,
            onSelectTab = viewModel::selectTab,
            onToggleAll = viewModel::toggleAll,
            onToggle = viewModel::toggle,
        )
    }
}

/** The sheet's body, stateless: header rings and Total, the three tabs, the open list. */
@Composable
fun FilterSheet(
    state: FilterSheetUiState,
    onSelectTab: (FilterTab) -> Unit,
    onToggleAll: () -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing

    Column(modifier = modifier.fillMaxWidth()) {
        SheetHeader(state)

        SegmentedTabs(
            options = listOf(
                stringResource(R.string.filter_tab_income),
                stringResource(R.string.filter_tab_expenses),
                stringResource(R.string.filter_tab_account),
            ),
            selectedIndex = state.tab.ordinal,
            onSelect = { onSelectTab(FilterTab.entries[it]) },
            modifier = Modifier.padding(horizontal = spacing.screen, vertical = spacing.sm),
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            when (state.tab) {
                FilterTab.INCOME -> checkList(state.allIncome, state.incomeRows, onToggleAll, onToggle)
                FilterTab.EXPENSES -> checkList(state.allExpense, state.expenseRows, onToggleAll, onToggle)
                FilterTab.ACCOUNT -> accountList(state, onToggleAll, onToggle)
            }
        }
    }
}

@Composable
private fun SheetHeader(state: FilterSheetUiState) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val period = periodLabel(state.range)
    val incomeLabel = stringResource(R.string.label_income)
    val expenseLabel = stringResource(R.string.label_expenses)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(text = period, style = SakuTheme.typography.heading, color = colors.ink, maxLines = 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            ShareRing(
                label = incomeLabel,
                amount = state.income.amount,
                percent = state.income.percent,
                type = TxnType.INCOME,
                contentDescription = ringDescription(incomeLabel, state.income, period),
            )
            ShareRing(
                label = expenseLabel,
                amount = state.expense.amount,
                percent = state.expense.percent,
                type = TxnType.EXPENSE,
                contentDescription = ringDescription(expenseLabel, state.expense, period),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.label_total),
                    style = SakuTheme.typography.caption,
                    color = colors.ink2,
                    maxLines = 1,
                )
                // A net, not a direction: signed and in neutral ink, as in the period header.
                MoneyText(
                    money = state.total,
                    style = SakuTheme.typography.amountMedium,
                    withPrefix = false,
                    signed = true,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

@Composable
private fun ringDescription(label: String, figure: RingFigure, period: String): String {
    val amount = formatIdr(figure.amount)
    val percent = figure.percent ?: return stringResource(R.string.filter_ring_plain, label, amount)
    return stringResource(R.string.filter_ring_share, label, amount, percent, label.lowercase(), period)
}

private fun LazyListScope.checkList(
    allTicked: Boolean,
    rows: List<CheckRow>,
    onToggleAll: () -> Unit,
    onToggle: (Long) -> Unit,
) {
    item(key = "all") { AllRow(allTicked, onToggleAll) }
    if (rows.isEmpty()) {
        item(key = "none") {
            EmptyState(icon = Icons.Filled.FilterList, message = stringResource(R.string.filter_no_items))
        }
    }
    items(count = rows.size, key = { rows[it].id }) { index ->
        val row = rows[index]
        CheckLine(ticked = row.ticked, onToggle = { onToggle(row.id) }) {
            ColorSwatch(row.colorSlot)
            Name(row.name)
        }
    }
}

private fun LazyListScope.accountList(
    state: FilterSheetUiState,
    onToggleAll: () -> Unit,
    onToggle: (Long) -> Unit,
) {
    item(key = "all") { AllRow(state.allAccounts, onToggleAll) }
    state.accountGroups.forEach { group ->
        item(key = "group-${group.type}") { GroupHeading(group.type) }
        items(count = group.rows.size, key = { group.rows[it].id }) { index ->
            val row = group.rows[index]
            CheckLine(ticked = row.ticked, onToggle = { onToggle(row.id) }) {
                ColorSwatch(row.colorSlot)
                Column(modifier = Modifier.weight(1f)) {
                    Name(row.name)
                    AccountFigures(row.flows)
                }
            }
        }
    }
}

/**
 * One tappable line with a checkbox. The whole row toggles, with the checkbox state
 * exposed through [toggleable], so a screen reader hears "checked" and not only a colour.
 */
@Composable
private fun CheckLine(
    ticked: Boolean,
    onToggle: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val spacing = SakuTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = ticked, role = Role.Checkbox, onValueChange = { onToggle() })
            .defaultMinSize(minHeight = spacing.touchTarget)
            .padding(horizontal = spacing.screen, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Checkbox(
            checked = ticked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = SakuTheme.colors.accent,
                uncheckedColor = SakuTheme.colors.ink3,
            ),
        )
        content()
    }
}

@Composable
private fun AllRow(ticked: Boolean, onToggle: () -> Unit) {
    CheckLine(ticked = ticked, onToggle = onToggle) {
        Text(
            text = stringResource(R.string.filter_all),
            style = SakuTheme.typography.bodyStrong,
            color = SakuTheme.colors.ink,
        )
    }
    HorizontalDivider(thickness = 1.dp, color = SakuTheme.colors.line)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Name(name: String) {
    Text(
        text = name,
        style = SakuTheme.typography.body,
        color = SakuTheme.colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Name(name: String) {
    Text(
        text = name,
        style = SakuTheme.typography.body,
        color = SakuTheme.colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GroupHeading(type: AccountType) {
    Text(
        text = stringResource(
            when (type) {
                AccountType.CASH -> R.string.accounts_type_cash
                AccountType.BANK -> R.string.accounts_type_bank
                AccountType.INVESTMENT -> R.string.accounts_type_investment
                AccountType.LIABILITY -> R.string.accounts_type_liability
            },
        ),
        style = SakuTheme.typography.label,
        color = SakuTheme.colors.ink2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SakuTheme.spacing.screen, vertical = SakuTheme.spacing.sm),
    )
}

/** Income and Expenses on the first line, Transfer-In and Transfer-Out on the second. */
@Composable
private fun AccountFigures(flows: AccountFlows) {
    FigureLine(
        stringResource(R.string.label_income) to flows.income,
        stringResource(R.string.label_expenses) to flows.expense,
        TxnType.INCOME,
        TxnType.EXPENSE,
    )
    FigureLine(
        stringResource(R.string.filter_transfer_in) to flows.transferIn,
        stringResource(R.string.filter_transfer_out) to flows.transferOut,
        TxnType.TRANSFER,
        TxnType.TRANSFER,
    )
}

@Composable
private fun FigureLine(
    first: Pair<String, Money>,
    second: Pair<String, Money>,
    firstType: TxnType,
    secondType: TxnType,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md)) {
        LabelledFigure(first.first, first.second, firstType, Modifier.weight(1f))
        LabelledFigure(second.first, second.second, secondType, Modifier.weight(1f))
    }
}

@Composable
private fun LabelledFigure(label: String, amount: Money, type: TxnType, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = SakuTheme.typography.caption, color = SakuTheme.colors.ink2, maxLines = 1)
        MoneyText(
            money = amount,
            type = type,
            style = SakuTheme.typography.amountSmall,
            withPrefix = false,
            spokenType = label,
        )
    }
}

@SakuPreview
@Composable
private fun FilterSheetPreview() {
    val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    SakuPreviewSurface(padded = false) {
        FilterSheet(
            state = FilterSheetUiState(
                range = october,
                tab = FilterTab.ACCOUNT,
                income = RingFigure(Money(8_000_000), 80),
                expense = RingFigure(Money(1_500_000), 25),
                total = Money(6_500_000),
                allAccounts = false,
                accountGroups = listOf(
                    AccountCheckGroup(
                        AccountType.BANK,
                        listOf(
                            AccountCheckRow(
                                1, "BCA", ColorSlot(0), true,
                                AccountFlows(1, Money(8_000_000), Money(2_400_000), Money(300_000), Money(500_000)),
                            ),
                            AccountCheckRow(2, "Jago", ColorSlot(1), false, AccountFlows.none(2)),
                        ),
                    ),
                ),
                loading = false,
            ),
            onSelectTab = {},
            onToggleAll = {},
            onToggle = {},
        )
    }
}
