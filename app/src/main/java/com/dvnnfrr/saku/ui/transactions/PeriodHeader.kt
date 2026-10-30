package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.PeriodBar
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrSigned

/**
 * Period navigation over what is left over, with what came in and went out beside it.
 *
 * "Left over" is the largest figure on the screen because it is the question a cashbook
 * answers first. It is a net, so it is signed and in neutral ink: colouring a negative
 * one in the expense colour would say "you spent this", which is not what it means.
 * In and Out are labelled, so they read unsigned in their direction's colour.
 *
 * In and Out sit beside the figure when they fit and wrap beneath it when they do not,
 * which is what a large font scale on a narrow phone needs.
 */
@Composable
fun PeriodHeader(
    range: DateRange,
    totals: PeriodTotals,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousDescription: String = "Previous period",
    nextDescription: String = "Next period",
    /** The screen's actions beside the period, such as filter and search. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        PeriodBar(
            range = range,
            onPrevious = onPrevious,
            onNext = onNext,
            previousDescription = previousDescription,
            nextDescription = nextDescription,
            trailing = trailing,
        )

        LeftOverFigures(
            totals = totals,
            modifier = Modifier.padding(start = spacing.screen, end = spacing.screen, bottom = spacing.md),
        )

        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

/**
 * "Left over" beside In and Out, as the period header draws them. Shared with the
 * Calendar's range summary, so a range reads the same way a month does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LeftOverFigures(
    totals: PeriodTotals,
    modifier: Modifier = Modifier,
    leftOverStyle: TextStyle = SakuTheme.typography.amountLarge,
) {
    val spacing = SakuTheme.spacing
    val leftOverLabel = stringResource(R.string.label_left_over)

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(modifier = Modifier.padding(end = spacing.lg)) {
            Caption(leftOverLabel)
            MoneyText(
                money = totals.net,
                sign = MoneySign.NET,
                style = leftOverStyle,
                contentDescription = "$leftOverLabel ${formatIdrSigned(totals.net)}",
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Bottom),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Figure(stringResource(R.string.label_in), totals.income, TxnType.INCOME)
            Figure(stringResource(R.string.label_out), totals.expense, TxnType.EXPENSE)
        }
    }
}

@Composable
private fun Figure(label: String, amount: Money, type: TxnType) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Caption(label)
        MoneyText(
            money = amount,
            type = type,
            style = SakuTheme.typography.amountSmall,
            contentDescription = "$label ${formatIdr(amount)}",
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = SakuTheme.typography.caption,
        color = SakuTheme.colors.ink2,
        maxLines = 1,
    )
}

@SakuPreview
@Composable
private fun PeriodHeaderPreview() {
    SakuPreviewSurface(padded = false) {
        PeriodHeader(
            range = DateRange.ofMonth(kotlinx.datetime.LocalDate(2026, 10, 1)),
            totals = PeriodTotals(income = Money(16_500_000), expense = Money(14_026_400)),
            onPrevious = {},
            onNext = {},
        )
    }
}
