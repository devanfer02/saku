package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PERMILLE_WHOLE
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.ui.shared.periodLabel
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.theme.formatIdrCompact
import com.dvnnfrr.saku.ui.theme.formatIdrSigned
import com.dvnnfrr.saku.ui.transactions.shortLabel
import kotlinx.datetime.LocalDate

/**
 * Six months ending with the selected one, as paired income and expense bars on one
 * scale, each month's short name and compact net beneath.
 *
 * The selected month is drawn at full strength and the other five muted, so the eye
 * lands on the month the figures below describe and reads the rest as context. A month
 * before any history is an empty slot on the baseline rather than two zero bars: "you
 * logged nothing" and "there was nothing yet" are different facts.
 *
 * Every month is a tap target the full column wide and speaks its own figures, so the
 * chart is never colour-only and never pointer-only.
 */
@Composable
fun SixMonthStrip(
    months: List<StripMonth>,
    onSelectMonth: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SakuTheme.spacing.sm, vertical = SakuTheme.spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { month ->
            MonthColumn(
                month = month,
                onClick = { onSelectMonth(month.range) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthColumn(month: StripMonth, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val name = periodLabel(month.range)
    val description = if (month.empty) {
        stringResource(R.string.stats_strip_month_empty, name)
    } else {
        stringResource(
            R.string.stats_strip_month,
            name,
            formatIdr(month.income),
            formatIdr(month.expense),
            formatIdrSigned(month.net),
        )
    }
    val selectLabel = stringResource(R.string.stats_strip_select)
    val textColor = if (month.selected) colors.ink else colors.ink2

    Column(
        modifier = modifier
            .heightIn(min = spacing.touchTarget)
            .clickable(onClickLabel = selectLabel, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = month.selected
            }
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Bars(month)
        Text(
            text = month.range.start.month.shortLabel(),
            style = SakuTheme.typography.caption,
            color = textColor,
            maxLines = 1,
        )
        // An empty slot keeps the line so every column stays the same height.
        Text(
            text = if (month.empty) "" else formatIdrCompact(month.net),
            style = SakuTheme.typography.caption,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun Bars(month: StripMonth) {
    val colors = SakuTheme.colors
    // Muted by alpha on the same tokens, so both themes keep their own validated hues.
    val strength = if (month.selected) 1f else MUTED_ALPHA

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.height(BAR_AREA),
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!month.empty) {
                Bar(month.incomePermille, colors.income.copy(alpha = strength))
                Bar(month.expensePermille, colors.expense.copy(alpha = strength))
            }
        }
        Box(
            modifier = Modifier
                .width(BAR_WIDTH * 2 + BAR_GAP + BASELINE_OVERHANG)
                .height(1.dp)
                .background(colors.line),
        )
    }
}

@Composable
private fun Bar(permille: Int, color: Color) {
    Box(
        modifier = Modifier
            .width(BAR_WIDTH)
            .fillMaxHeight(permille.coerceIn(0, PERMILLE_WHOLE) / PERMILLE_WHOLE.toFloat())
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(color),
    )
}

private const val MUTED_ALPHA = 0.4f
private val BAR_AREA = 72.dp
private val BAR_WIDTH = 10.dp
private val BAR_GAP = 3.dp
private val BASELINE_OVERHANG = 8.dp

@SakuPreview
@Composable
private fun SixMonthStripPreview() {
    val october = DateRange.ofMonth(LocalDate(2026, 10, 1))
    val window = stripWindow(october)
    val totals = listOf(
        PeriodTotals(Money(9_000_000), Money(8_200_000)),
        PeriodTotals(Money(16_000_000), Money(8_900_000)),
        PeriodTotals(Money(8_000_000), Money(8_300_000)),
        PeriodTotals(Money(8_000_000), Money(9_400_000)),
        PeriodTotals(Money(9_000_000), Money(7_600_000)),
        PeriodTotals(Money(16_500_000), Money(14_026_400)),
    )
    val early = DateRange.ofMonth(LocalDate(2025, 8, 1))
    val earlyWindow = stripWindow(early)
    SakuPreviewSurface(padded = false) {
        Column {
            SixMonthStrip(foldStrip(window, totals, PeriodTotals(Money(1), Money.ZERO), october), onSelectMonth = {})
            SixMonthStrip(
                foldStrip(
                    earlyWindow,
                    List(4) { PeriodTotals.EMPTY } + totals.takeLast(2),
                    PeriodTotals.EMPTY,
                    early,
                ),
                onSelectMonth = {},
            )
        }
    }
}
