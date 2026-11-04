package com.dvnnfrr.saku.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.shared.MoneyText
import com.dvnnfrr.saku.ui.shared.periodName
import com.dvnnfrr.saku.ui.theme.MoneySign
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdr
import com.dvnnfrr.saku.ui.transactions.dateLabel
import kotlinx.datetime.LocalDate

/**
 * The price card between the search summary and the tabs. Its header, the latest unit
 * price, is always shown and toggles the rest: range chips (6M, 1Y, 3Y, All, those that
 * cut the history), the chart, the lowest, the highest and the change since the range's
 * first point. Every figure follows the range; [range] null is [defaultRange].
 *
 * TalkBack reads one sentence for the whole card from the header. The chips and the chart
 * speak for themselves; the figures, which only repeat the sentence, are hidden from it.
 */
@Composable
fun PriceCard(
    history: PriceHistory,
    expanded: Boolean,
    onToggle: () -> Unit,
    range: PriceRange?,
    onSelectRange: (PriceRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val typography = SakuTheme.typography
    val ranges = remember(history) { history.ranges() }
    val shownRange = range?.takeIf { it in ranges } ?: history.defaultRange()
    val price = remember(history, shownRange) { history.inRange(shownRange) }
    val chart = remember(price) { priceChart(price.points) }
    // The tapped point; a new item or range starts with none.
    var selectedPoint by remember(history.itemKey, shownRange) { mutableStateOf<Int?>(null) }
    val spoken = spokenSummary(price)
    val stateText = stringResource(if (expanded) R.string.price_expanded else R.string.price_collapsed)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screen)
            .clip(RoundedCornerShape(spacing.corner))
            .background(colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.touchTarget)
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.price_hide else R.string.price_show),
                    role = Role.Button,
                    onClick = onToggle,
                )
                .clearAndSetSemantics {
                    contentDescription = spoken
                    stateDescription = stateText
                }
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Which item, since search may hold several: "Unit price, Indomie Goreng Special".
            Text(
                text = stringResource(R.string.price_title_of, price.name),
                style = typography.label,
                color = colors.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MoneyText(price.latest.unitPrice, style = typography.amountMedium)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = colors.ink2,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (ranges.size >= 2) {
                    RangeChips(ranges, shownRange, onSelectRange)
                }
                PriceLineChart(chart = chart, selected = selectedPoint, onSelect = { selectedPoint = it })
                Column(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Figure(stringResource(R.string.price_lowest), price.lowest.unitPrice, price.lowest.date.dateLabel())
                    Figure(stringResource(R.string.price_highest), price.highest.unitPrice, price.highest.date.dateLabel())
                    ChangeFigure(price)
                }
            }
        }
    }
}

/** One chip per offered range, the shown one selected. */
@Composable
private fun RangeChips(ranges: List<PriceRange>, shown: PriceRange, onSelect: (PriceRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.sm)) {
        ranges.forEach { range ->
            val spoken = stringResource(range.spokenRes)
            FilterChip(
                selected = range == shown,
                onClick = { onSelect(range) },
                label = { Text(text = stringResource(range.labelRes), maxLines = 1) },
                // 32 dp to see; Material keeps the 48 dp touch target around it.
                modifier = Modifier.semantics { contentDescription = spoken },
            )
        }
    }
}

private val PriceRange.labelRes: Int
    get() = when (this) {
        PriceRange.SIX_MONTHS -> R.string.price_range_6m
        PriceRange.ONE_YEAR -> R.string.price_range_1y
        PriceRange.THREE_YEARS -> R.string.price_range_3y
        PriceRange.ALL -> R.string.price_range_all
    }

private val PriceRange.spokenRes: Int
    get() = when (this) {
        PriceRange.SIX_MONTHS -> R.string.price_range_6m_spoken
        PriceRange.ONE_YEAR -> R.string.price_range_1y_spoken
        PriceRange.THREE_YEARS -> R.string.price_range_3y_spoken
        PriceRange.ALL -> R.string.price_range_all_spoken
    }

/** A label on the left; the amount, and under it its date, on the right. */
@Composable
private fun Figure(label: String, amount: Money, date: String) {
    val colors = SakuTheme.colors
    val typography = SakuTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SakuTheme.spacing.xs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md),
    ) {
        Text(text = label, style = typography.body, color = colors.ink2, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(amount, style = typography.amountSmall)
            Text(text = date, style = typography.caption, color = colors.ink2)
        }
    }
}

/** `Since 12 Jul 2026` on the left; `+Rp 3.900` and `+11%` on the right, in ink: a price is not income. */
@Composable
private fun ChangeFigure(price: PriceHistory) {
    val colors = SakuTheme.colors
    val typography = SakuTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SakuTheme.spacing.xs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md),
    ) {
        Text(
            text = stringResource(R.string.price_since, price.first.date.dateLabel()),
            style = typography.body,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(price.change, style = typography.amountSmall, sign = MoneySign.NET, color = colors.ink)
            price.changePercent?.let { percent ->
                Text(text = signedPercent(percent), style = typography.caption, color = colors.ink2)
            }
        }
    }
}

/** `+11%`, `−4%`, `0%`, with the same minus sign as the amounts. */
private fun signedPercent(percent: Int): String = when {
    percent > 0 -> "+$percent%"
    percent < 0 -> "−${-percent}%"
    else -> "0%"
}

@Composable
private fun spokenSummary(price: PriceHistory): String {
    val latest = price.latest.date
    val since = spokenWhen(price.first.date, latest)
    val percent = price.changePercent
    val change = when {
        price.change.isZero -> stringResource(R.string.price_spoken_same, since)
        percent != null && percent > 0 -> stringResource(R.string.price_spoken_up_percent, percent, since)
        percent != null && percent < 0 -> stringResource(R.string.price_spoken_down_percent, -percent, since)
        price.change.isPositive -> stringResource(R.string.price_spoken_up_amount, formatIdr(price.change), since)
        else -> stringResource(R.string.price_spoken_down_amount, formatIdr(price.change.absolute), since)
    }
    return stringResource(
        R.string.price_spoken,
        price.name,
        formatIdr(price.latest.unitPrice),
        formatIdr(price.lowest.unitPrice),
        spokenAt(price.lowest.date, latest),
        formatIdr(price.highest.unitPrice),
        spokenAt(price.highest.date, latest),
        change,
    )
}

/** `in July`, or `on 25 Oct 2026` within the latest point's month. */
@Composable
private fun spokenAt(date: LocalDate, latest: LocalDate): String =
    stringResource(
        if (isSameMonth(date, latest)) R.string.price_on_day else R.string.price_in_month,
        spokenWhen(date, latest),
    )

private fun isSameMonth(a: LocalDate, b: LocalDate): Boolean = a.year == b.year && a.month == b.month

/**
 * The month of [date], with its year only when that differs from [latest]'s. Within the
 * latest point's own month a month name says nothing, so the day is spoken instead.
 */
private fun spokenWhen(date: LocalDate, latest: LocalDate): String {
    if (isSameMonth(date, latest)) return date.dateLabel()
    return periodName(DateRange.ofMonth(date), reference = DateRange.ofMonth(latest))
}

@SakuPreview
@Composable
private fun PriceCardPreview() {
    val points = listOf(
        PricePoint(LocalDate(2026, 7, 12), Money(35_000)),
        PricePoint(LocalDate(2026, 8, 9), Money(36_000)),
        PricePoint(LocalDate(2026, 9, 14), Money(41_000)),
        PricePoint(LocalDate(2026, 10, 25), Money(38_900)),
    )
    SakuPreviewSurface(padded = false) {
        PriceCard(
            history = PriceHistory(
                itemKey = "MINYAKGORENG2L",
                name = "Minyak Goreng 2L",
                points = points,
                lowest = points[0],
                highest = points[2],
                change = Money(3_900),
                changePercent = 11,
            ),
            expanded = true,
            onToggle = {},
            range = null,
            onSelectRange = {},
        )
    }
}
