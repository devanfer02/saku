package com.dvnnfrr.saku.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.dvnnfrr.saku.domain.ChartStyle
import com.dvnnfrr.saku.ui.theme.forSlotOrNeutral
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.ui.filter.FilterAction
import com.dvnnfrr.saku.ui.filter.FilterBar
import com.dvnnfrr.saku.ui.filter.FilterSheet
import com.dvnnfrr.saku.ui.filter.FilteredEmptyState
import com.dvnnfrr.saku.ui.shared.EmptyState
import com.dvnnfrr.saku.ui.shared.PeriodBar
import com.dvnnfrr.saku.ui.shared.RankedRow
import com.dvnnfrr.saku.ui.shared.SegmentedTabs
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlinx.datetime.LocalDate

/** The four period modes, in the order the selector shows them. */
private val MODES = listOf(
    PeriodMode.WEEKLY,
    PeriodMode.MONTHLY,
    PeriodMode.ANNUALLY,
    PeriodMode.CUSTOM,
)

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    var filtering by rememberSaveable { mutableStateOf(false) }

    StatsScreen(
        state = state,
        onPrevious = viewModel::previousPeriod,
        onNext = viewModel::nextPeriod,
        onSelectMode = viewModel::selectMode,
        onSelectRange = viewModel::selectRange,
        onSelectSide = viewModel::selectSide,
        onOpenCategory = viewModel::openCategory,
        onOpenOther = viewModel::openOther,
        onCloseDrill = viewModel::closeDrill,
        onOpenFilter = { filtering = true },
        onClearFilter = viewModel::clearFilter,
        onSelectPeriod = viewModel::selectPeriod,
        onToggleStrip = viewModel::toggleStrip,
        onSelectChartStyle = viewModel::selectChartStyle,
        modifier = modifier,
    )

    if (filtering) {
        FilterSheet(range = state.range, onDismiss = { filtering = false })
    }
}

@Composable
fun StatsScreen(
    state: StatsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectMode: (PeriodMode) -> Unit,
    onSelectRange: (LocalDate, LocalDate) -> Unit,
    onSelectSide: (StatsSide) -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenOther: () -> Unit,
    onCloseDrill: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFilter: () -> Unit = {},
    onClearFilter: () -> Unit = {},
    onSelectPeriod: (DateRange) -> Unit = {},
    onToggleStrip: () -> Unit = {},
    onSelectChartStyle: (ChartStyle) -> Unit = {},
) {
    var pickingRange by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        PeriodBar(
            range = state.range,
            onPrevious = onPrevious,
            onNext = onNext,
            previousDescription = stringResource(R.string.stats_previous_period),
            nextDescription = stringResource(R.string.stats_next_period),
            trailing = { FilterAction(active = state.filter.isActive, onClick = onOpenFilter) },
        )
        RangeDates(state.range)
        FilterBar(filter = state.filter, onClear = onClearFilter)

        SegmentedTabs(
            options = MODES.map { stringResource(it.labelRes) },
            selectedIndex = MODES.indexOf(state.range.mode).coerceAtLeast(0),
            onSelect = { index ->
                val mode = MODES[index]
                // Picking Period has no canonical range to derive, so it asks for one
                // instead of guessing. Tapping it again re-opens the picker, which is
                // the only way back to it once the mode is already Period.
                if (mode == PeriodMode.CUSTOM) pickingRange = true else onSelectMode(mode)
            },
            modifier = Modifier.padding(SakuTheme.spacing.md),
        )

        val drill = state.drill
        if (drill == null) {
            Overview(
                state = state,
                onSelectSide = onSelectSide,
                onSelectPeriod = onSelectPeriod,
                onToggleStrip = onToggleStrip,
                onSelectChartStyle = onSelectChartStyle,
                onOpenCategory = onOpenCategory,
                onOpenOther = onOpenOther,
                onClearFilter = onClearFilter,
            )
        } else {
            BackHandler(onBack = onCloseDrill)
            SideTabs(state.side, onSelectSide)
            DrillPane(
                drill = drill,
                range = state.range,
                side = state.side,
                onBack = onCloseDrill,
                onOpenCategory = onOpenCategory,
                onSelectPeriod = onSelectPeriod,
            )
        }
    }

    if (pickingRange) {
        RangePickerDialog(
            initial = state.range,
            onPick = { start, end ->
                onSelectRange(start, end)
                pickingRange = false
            },
            onDismiss = { pickingRange = false },
        )
    }
}

@Composable
private fun SideTabs(side: StatsSide, onSelectSide: (StatsSide) -> Unit) {
    SegmentedTabs(
        options = listOf(
            stringResource(R.string.label_expenses),
            stringResource(R.string.label_income),
        ),
        selectedIndex = side.ordinal,
        onSelect = { onSelectSide(StatsSide.entries[it]) },
        modifier = Modifier.padding(SakuTheme.spacing.md),
    )
}

/**
 * The strip, the side, the headline and the ranked bars, scrolling as one list.
 *
 * One list rather than a fixed header over a short one: with the strip showing and a
 * font scale of 1.3, a fixed header left the ranked rows a sliver of the screen.
 */
@Composable
private fun Overview(
    state: StatsUiState,
    onSelectSide: (StatsSide) -> Unit,
    onSelectPeriod: (DateRange) -> Unit,
    onToggleStrip: () -> Unit,
    onSelectChartStyle: (ChartStyle) -> Unit,
    onOpenCategory: (Long) -> Unit,
    onOpenOther: () -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val otherLabel = stringResource(R.string.stats_other)
    val listState = rememberLazyListState()
    val hasStrip = state.strip.isNotEmpty()

    // The strip's totals arrive after the rest of the overview, and a keyed list stays
    // anchored on "side", its first item until then, so the strip landed above the
    // viewport. Reveal it only when the list is still at its top, never mid-scroll.
    LaunchedEffect(hasStrip) {
        val stillAtTop = listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset == 0
        if (hasStrip && stillAtTop) listState.scrollToItem(0)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        if (state.strip.isNotEmpty()) {
            item(key = "strip") {
                StripHeader(columns = state.strip, expanded = state.stripExpanded, onToggle = onToggleStrip)
                if (state.stripExpanded) PeriodStrip(columns = state.strip, onSelectPeriod = onSelectPeriod)
                HorizontalDivider(thickness = 1.dp, color = SakuTheme.colors.line)
            }
        }
        item(key = "side") { SideTabs(state.side, onSelectSide) }
        item(key = "headline") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatsHeadline(
                    side = state.side,
                    range = state.range,
                    total = state.sideTotal,
                    comparison = state.comparisonLine,
                    modifier = Modifier.weight(1f),
                )
                ChartStyleToggle(
                    style = state.chartStyle,
                    onSelect = onSelectChartStyle,
                    modifier = Modifier.padding(end = SakuTheme.spacing.sm),
                )
            }
        }

        if (state.composition.isEmpty) {
            item(key = "empty") { EmptyComposition(state, onClearFilter) }
            return@LazyColumn
        }

        if (state.chartStyle == ChartStyle.PIE) {
            item(key = "pie") {
                val colors = SakuTheme.colors
                StatsPie(
                    slices = state.composition.rows.map {
                        PieSlice(colors.forSlotOrNeutral(it.colorSlot), it.sweepTenths, it.name ?: otherLabel, it.percent, it.total)
                    },
                    centerLabel = stringResource(
                        when (state.side) {
                            StatsSide.EXPENSES -> R.string.stats_pie_spent
                            StatsSide.INCOME -> R.string.stats_pie_earned
                        },
                    ),
                    centerValue = formatIdr(state.sideTotal),
                )
            }
        }

        categoryRows(state.composition.rows, otherLabel, onOpenCategory, onOpenOther)
    }
}

/**
 * One 48 dp pie button beside the headline that turns the pie on and off; the ranked
 * rows stay either way. A switch for TalkBack: it speaks "Pie chart" and on or off.
 */
@Composable
private fun ChartStyleToggle(
    style: ChartStyle,
    onSelect: (ChartStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val pieOn = style == ChartStyle.PIE
    Box(
        modifier = modifier
            .size(SakuTheme.spacing.touchTarget)
            .clip(CircleShape)
            .then(if (pieOn) Modifier.background(colors.surfaceAlt) else Modifier)
            .toggleable(
                value = pieOn,
                role = Role.Switch,
                onValueChange = { on -> onSelect(if (on) ChartStyle.PIE else ChartStyle.BARS) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PieChart,
            contentDescription = stringResource(R.string.stats_chart_pie),
            tint = if (pieOn) colors.accent else colors.ink2,
        )
    }
}

@Composable
private fun EmptyComposition(state: StatsUiState, onClearFilter: () -> Unit) {
    if (state.filter.isActive) {
        FilteredEmptyState(
            message = stringResource(R.string.filter_empty_period, explicitRangeLabel(state.range)),
            onClear = onClearFilter,
        )
        return
    }
    // Named, not generic. "No data" leaves the user wondering whether the period is
    // empty or the screen is broken.
    EmptyState(
        icon = Icons.AutoMirrored.Filled.ReceiptLong,
        message = stringResource(
            when (state.side) {
                StatsSide.EXPENSES -> R.string.stats_empty_expenses
                StatsSide.INCOME -> R.string.stats_empty_income
            },
            explicitRangeLabel(state.range),
        ),
    )
}

private fun LazyListScope.categoryRows(
    rows: List<RankedCategory>,
    otherLabel: String,
    onOpenCategory: (Long) -> Unit,
    onOpenOther: () -> Unit,
) {
    items(
        count = rows.size,
        key = { index -> rows[index].categoryId ?: OTHER_KEY },
    ) { index ->
        val row = rows[index]
        CategoryRow(
            row = row,
            otherLabel = otherLabel,
            onClick = { row.categoryId?.let(onOpenCategory) ?: onOpenOther() },
        )
    }
}

/** No category id can be negative, so this cannot collide with a real key. */
private const val OTHER_KEY = -1L

/**
 * One ranked category: colour, name, whole percent, amount and a bar scaled to the
 * largest row. The bar replaced the donut; the row was already its legend.
 *
 * Every row opens a detail, so every row ends in a chevron and says so when spoken:
 * a category opens its own detail, "Other" opens the categories it combines. The
 * pressed state is the ripple `RankedRow` gets from being clickable.
 */
@Composable
internal fun CategoryRow(
    row: RankedCategory,
    otherLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = row.name ?: otherLabel
    val spoken = stringResource(
        R.string.stats_row_description,
        name,
        row.percent,
        formatIdr(row.total),
    ) + ". " + stringResource(if (row.isOther) R.string.stats_row_opens_other else R.string.stats_row_opens_category)
    RankedRow(
        colorSlot = row.colorSlot,
        name = name,
        value = row.total,
        sharePercent = row.percent,
        barPermille = row.barPermille,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SakuTheme.colors.ink2,
            )
        },
        // The swatch carries no name of its own, so without this the row is read out as
        // three loose fragments and the share is heard as a bare number.
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = spoken
            role = Role.Button
        },
    )
}

/** Names a block within the drill: the strip, then the transactions. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SakuTheme.typography.label,
        color = SakuTheme.colors.ink2,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = SakuTheme.spacing.screen,
                vertical = SakuTheme.spacing.sm,
            ),
    )
}

internal val PeriodMode.labelRes: Int
    get() = when (this) {
        PeriodMode.WEEKLY -> R.string.stats_mode_weekly
        PeriodMode.MONTHLY -> R.string.stats_mode_monthly
        PeriodMode.ANNUALLY -> R.string.stats_mode_annually
        PeriodMode.CUSTOM -> R.string.stats_mode_custom
    }

private fun previewComposition(): RankedComposition = rankComposition(
    listOf(
        previewTotal(1, "Pemberian", 0, 4_770_000, 3_401),
        previewTotal(2, "Gadget", 1, 2_805_000, 2_000),
        previewTotal(3, "Jajan & Hiburan", 2, 2_244_000, 1_600),
        previewTotal(4, "Gym", 3, 2_244_000, 1_600),
        previewTotal(5, "Makan & Minum", 4, 1_263_400, 901),
        previewTotal(6, "Kebutuhan Kos", 5, 700_000, 499),
    ),
)

private fun previewTotal(id: Long, name: String, slot: Int, amount: Long, basisPoints: Int) =
    com.dvnnfrr.saku.domain.CategoryTotal(id, name, ColorSlot(slot), Money(amount), basisPoints)

@SakuPreview
@Composable
private fun StatsScreenPreview() {
    val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    val totals = PeriodTotals(Money(16_500_000), Money(14_026_400))
    SakuPreviewSurface(padded = false) {
        StatsScreen(
            state = StatsUiState(
                range = october,
                totals = totals,
                comparison = PeriodComparison(
                    previousRange = october.previous(),
                    previous = PeriodTotals(income = Money(9_000_000), expense = Money(17_755_000)),
                    current = totals,
                ),
                composition = previewComposition(),
                strip = foldStrip(previewHistory(), october),
                loading = false,
            ),
            onPrevious = {},
            onNext = {},
            onSelectMode = {},
            onSelectRange = { _, _ -> },
            onSelectSide = {},
            onOpenCategory = {},
            onOpenOther = {},
            onCloseDrill = {},
        )
    }
}

@SakuPreview
@Composable
private fun StatsScreenEmptyPreview() {
    val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
    SakuPreviewSurface(padded = false) {
        StatsScreen(
            state = StatsUiState(range = august, loading = false),
            onPrevious = {},
            onNext = {},
            onSelectMode = {},
            onSelectRange = { _, _ -> },
            onSelectSide = {},
            onOpenCategory = {},
            onOpenOther = {},
            onCloseDrill = {},
        )
    }
}
