package com.dvnnfrr.saku.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.ui.shared.Donut
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
        modifier = modifier,
    )
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
) {
    var pickingRange by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        PeriodBar(
            range = state.range,
            onPrevious = onPrevious,
            onNext = onNext,
            previousDescription = stringResource(R.string.stats_previous_period),
            nextDescription = stringResource(R.string.stats_next_period),
        )
        RangeDates(state.range)

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

        StatsFigures(totals = state.totals, comparison = state.comparison)
        HorizontalDivider(thickness = 1.dp, color = SakuTheme.colors.line)

        SegmentedTabs(
            options = listOf(
                stringResource(R.string.label_expenses),
                stringResource(R.string.label_income),
            ),
            selectedIndex = state.side.ordinal,
            onSelect = { onSelectSide(StatsSide.entries[it]) },
            modifier = Modifier.padding(SakuTheme.spacing.md),
        )

        val drill = state.drill
        if (drill == null) {
            Composition(
                state = state,
                onOpenCategory = onOpenCategory,
                onOpenOther = onOpenOther,
            )
        } else {
            BackHandler(onBack = onCloseDrill)
            DrillPane(
                drill = drill,
                range = state.range,
                side = state.side,
                onBack = onCloseDrill,
                onOpenCategory = onOpenCategory,
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

/**
 * The donut over its ranked list.
 *
 * The list is the donut's legend, which is what stops the chart conveying identity by
 * colour alone — the shared `Donut` documents that it must never ship without one.
 */
@Composable
private fun Composition(
    state: StatsUiState,
    onOpenCategory: (Long) -> Unit,
    onOpenOther: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val otherLabel = stringResource(R.string.stats_other)
    val sideLabel = stringResource(
        when (state.side) {
            StatsSide.EXPENSES -> R.string.label_expenses
            StatsSide.INCOME -> R.string.label_income
        },
    )

    if (state.composition.isEmpty) {
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
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "donut") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SakuTheme.spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Donut(
                    segments = donutSegments(state.composition.rows, otherLabel),
                    centerLabel = sideLabel,
                    centerValue = state.sideTotal,
                    // A segment opens the same drill its row does. `donutSegments` maps
                    // rows one-to-one, so the index the ring reports indexes the rows;
                    // a miss reports null and is ignored, because there is nothing to
                    // deselect when the tap navigates instead of selecting.
                    onSegmentSelect = { index ->
                        state.composition.rows.getOrNull(index ?: -1)?.let { row ->
                            row.categoryId?.let(onOpenCategory) ?: onOpenOther()
                        }
                    },
                )
            }
        }
        items(
            count = state.composition.rows.size,
            key = { index -> state.composition.rows[index].categoryId ?: OTHER_KEY },
        ) { index ->
            val row = state.composition.rows[index]
            CategoryRow(
                row = row,
                otherLabel = otherLabel,
                onClick = { row.categoryId?.let(onOpenCategory) ?: onOpenOther() },
            )
        }
    }
}

/** No category id can be negative, so this cannot collide with a real key. */
private const val OTHER_KEY = -1L

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
    )
    RankedRow(
        colorSlot = row.colorSlot,
        name = name,
        value = row.total,
        sharePercent = row.percent,
        onClick = onClick,
        // The swatch carries no name of its own, so without this the row is read out as
        // three loose fragments and the share is heard as a bare number.
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
    )
}

/** Names a block within the drill — the subcategory split, then the transactions. */
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
        previewTotal(1, "Perawatan Diri", 0, 1_791_900, 3_582),
        previewTotal(2, "Makan & Minum", 1, 1_257_700, 2_514),
        previewTotal(3, "Pemberian", 2, 1_050_000, 2_099),
        previewTotal(4, "Jajan & Hiburan", 3, 843_800, 1_687),
        previewTotal(5, "Obat", 4, 80_000, 159),
        previewTotal(6, "Kebutuhan Kos", 5, 38_000, 75),
    ),
)

private fun previewTotal(id: Long, name: String, slot: Int, amount: Long, basisPoints: Int) =
    com.dvnnfrr.saku.domain.CategoryTotal(id, name, ColorSlot(slot), Money(amount), basisPoints)

@SakuPreview
@Composable
private fun StatsScreenPreview() {
    val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
    SakuPreviewSurface(padded = false) {
        StatsScreen(
            state = StatsUiState(
                range = august,
                totals = PeriodTotals(income = Money(8_000_000), expense = Money(5_061_400)),
                comparison = PeriodComparison(
                    previousRange = august.previous(),
                    previous = PeriodTotals(income = Money(8_000_000), expense = Money(5_270_000)),
                    current = PeriodTotals(income = Money(8_000_000), expense = Money(5_061_400)),
                ),
                composition = previewComposition(),
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
