package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrSigned
import kotlinx.datetime.LocalDate

/**
 * Income, expenses and the net for the period, over a line comparing it to the one
 * before.
 *
 * The Total is signed and neutral. A net has no direction — colouring a negative one in
 * the expense red would read as "you spent this", which is not what the number means.
 */
@Composable
fun StatsFigures(
    totals: PeriodTotals,
    comparison: PeriodComparison,
    modifier: Modifier = Modifier,
) {
    val spacing = SakuTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Figure(
                label = stringResource(R.string.label_income),
                amount = totals.income,
                type = TxnType.INCOME,
                modifier = Modifier.weight(1f),
            )
            Figure(
                label = stringResource(R.string.label_expenses),
                amount = totals.expense,
                type = TxnType.EXPENSE,
                modifier = Modifier.weight(1f),
            )
            Figure(
                label = stringResource(R.string.label_total),
                amount = totals.net,
                type = null,
                modifier = Modifier.weight(1f),
            )
        }
        ComparisonLine(comparison)
    }
}

/**
 * How the period moved against the previous equivalent one.
 *
 * When the previous period held nothing the line says so rather than printing a
 * percentage, because there is no denominator to divide by and "+100%" against nothing
 * is a made-up figure.
 */
@Composable
private fun ComparisonLine(comparison: PeriodComparison, modifier: Modifier = Modifier) {
    val previousRange = comparison.previousRange ?: return
    val previousName = periodLabel(previousRange)
    val colors = SakuTheme.colors

    val text = if (!comparison.hasBaseline) {
        stringResource(R.string.stats_compare_none, previousName)
    } else {
        val income = comparison.incomeChangePercent
        val expense = comparison.expenseChangePercent
        val parts = listOfNotNull(
            income?.let { "${stringResource(R.string.label_income)} ${formatPercentChange(it)}" },
            expense?.let { "${stringResource(R.string.label_expenses)} ${formatPercentChange(it)}" },
        )
        stringResource(R.string.stats_compare_change, parts.joinToString("  ·  "), previousName)
    }

    Text(
        text = text,
        style = SakuTheme.typography.caption,
        color = colors.ink2,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Figure(
    label: String,
    amount: Money,
    type: TxnType?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = SakuTheme.typography.caption,
            color = SakuTheme.colors.ink2,
            maxLines = 1,
        )
        MoneyText(
            money = amount,
            // A directional figure is unsigned — its label already says which way it
            // goes, and a "+" on Income beside a bare Expenses figure reads as
            // inconsistent. The net carries its sign because the sign is the point.
            type = type,
            signed = type == null,
            withPrefix = false,
            style = SakuTheme.typography.amountSmall,
            contentDescription = "$label ${
                if (type == null) formatIdrSigned(amount) else formatIdr(amount)
            }",
        )
    }
}

/**
 * The period the figures cover, in full dates.
 *
 * Separate from the header above it, which names the period in words. Monthly reads
 * "August 2026" up there, and the boundaries still have to be visible — a user stepping
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
private fun StatsFiguresPreview() {
    val august = DateRange.ofMonth(LocalDate(2026, 8, 1))
    SakuPreviewSurface(padded = false) {
        Column {
            RangeDates(august)
            StatsFigures(
                totals = PeriodTotals(income = Money(8_000_000), expense = Money(5_061_400)),
                comparison = PeriodComparison(
                    previousRange = august.previous(),
                    previous = PeriodTotals(income = Money(8_000_000), expense = Money(5_270_000)),
                    current = PeriodTotals(income = Money(8_000_000), expense = Money(5_061_400)),
                ),
            )
            StatsFigures(
                totals = PeriodTotals(income = Money.ZERO, expense = Money.ZERO),
                comparison = PeriodComparison(august.previous(), PeriodTotals.EMPTY, PeriodTotals.EMPTY),
            )
        }
    }
}
