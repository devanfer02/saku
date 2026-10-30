package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.periodName
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import kotlinx.datetime.LocalDate

/**
 * The selected side's total for the period, as the largest figure on the screen, over
 * one line comparing it with the previous period of the same length.
 *
 * Only one side at a time: the side tabs already choose it, and three figures in a row
 * made every one of them small. The total is in its direction's colour because it is a
 * labelled total, which is where the expense colour is still spent.
 */
@Composable
fun StatsHeadline(
    side: StatsSide,
    range: DateRange,
    total: Money,
    comparison: ComparisonLine?,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        when (side) {
            StatsSide.EXPENSES -> R.string.stats_spent_in
            StatsSide.INCOME -> R.string.stats_earned_in
        },
        periodName(range),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakuTheme.spacing.screen, vertical = SakuTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
    ) {
        Text(text = label, style = SakuTheme.typography.caption, color = SakuTheme.colors.ink2)
        MoneyText(
            money = total,
            type = side.txnType,
            style = SakuTheme.typography.amountLarge,
            contentDescription = "$label ${formatIdr(total)}",
        )
        comparison?.let {
            Text(
                text = it.text(range),
                style = SakuTheme.typography.caption,
                color = SakuTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun ComparisonLine.text(current: DateRange): String {
    val previous = periodName(previousRange, reference = current)
    return when (this) {
        is ComparisonLine.Less -> stringResource(R.string.stats_compare_less, percent, previous)
        is ComparisonLine.More -> stringResource(R.string.stats_compare_more, percent, previous)
        is ComparisonLine.Same -> stringResource(R.string.stats_compare_same, previous)
        is ComparisonLine.NothingToCompare -> stringResource(R.string.stats_compare_none, previous)
    }
}

/**
 * The period the figures cover, in full dates.
 *
 * Separate from the header above it, which names the period in words. Monthly reads
 * "August 2026" up there, and the boundaries still have to be visible: a user stepping
 * through weeks cannot otherwise tell which seven days they are looking at.
 */
@Composable
fun RangeDates(range: DateRange, modifier: Modifier = Modifier) {
    val label = explicitRangeLabel(range)
    Text(
        text = label,
        style = SakuTheme.typography.caption,
        color = SakuTheme.colors.ink2,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakuTheme.spacing.screen)
            // The dates repeat what the header already said in words; announcing both
            // makes every period step a two-part utterance.
            .clearAndSetSemantics { contentDescription = label },
    )
}

@SakuPreview
@Composable
private fun StatsHeadlinePreview() {
    val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    val comparison = PeriodComparison(
        previousRange = october.previous(),
        previous = PeriodTotals(income = Money(8_000_000), expense = Money(17_755_000)),
        current = PeriodTotals(income = Money(16_500_000), expense = Money(14_026_400)),
    )
    SakuPreviewSurface(padded = false) {
        Column {
            RangeDates(october)
            StatsHeadline(
                side = StatsSide.EXPENSES,
                range = october,
                total = Money(14_026_400),
                comparison = comparisonLine(comparison, StatsSide.EXPENSES),
            )
            StatsHeadline(
                side = StatsSide.INCOME,
                range = october,
                total = Money.ZERO,
                comparison = comparisonLine(
                    PeriodComparison(october.previous(), PeriodTotals.EMPTY, PeriodTotals.EMPTY),
                    StatsSide.INCOME,
                ),
            )
        }
    }
}
