package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.shared.PeriodBar
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.forDirection
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrSigned

/**
 * Month navigation over the three figures for that month.
 *
 * The Total is signed and takes a neutral ink rather than the income or expense
 * colour. It is a net, not a direction — colouring a negative net in the expense red
 * would say "you spent this", which is not what the number means.
 */
@Composable
fun PeriodHeader(
    range: DateRange,
    totals: PeriodTotals,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Column(modifier = modifier.fillMaxWidth().background(colors.surface)) {
        PeriodBar(range = range, onPrevious = onPrevious, onNext = onNext)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screen, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
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
            NetFigure(
                label = stringResource(R.string.label_total),
                amount = totals.net,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

@Composable
private fun Figure(label: String, amount: Money, type: TxnType, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Caption(label)
        // Unsigned: the label already says which direction it is, and a "+" on the
        // Income figure beside an unsigned Expenses figure reads as inconsistent.
        Text(
            text = formatIdr(amount, withPrefix = false),
            style = SakuTheme.typography.amountSmall,
            color = SakuTheme.colors.forDirection(type),
            maxLines = 1,
        )
    }
}

@Composable
private fun NetFigure(label: String, amount: Money, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Caption(label)
        Text(
            text = formatIdrSigned(amount, withPrefix = false),
            style = SakuTheme.typography.amountSmall,
            color = SakuTheme.colors.ink,
            maxLines = 1,
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
            range = DateRange.ofMonth(kotlinx.datetime.LocalDate(2026, 8, 1)),
            totals = PeriodTotals(income = Money(8_000_000), expense = Money(5_061_400)),
            onPrevious = {},
            onNext = {},
        )
    }
}
