package com.dvnnfrr.saku.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.ui.theme.formatIdrSigned
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

/**
 * The month as a grid, one cell per day.
 *
 * This tab exists because half of a year of reference data was logged from
 * memory days later, with one twenty-day hole. A day with no transactions renders
 * blank rather than as a zero, so those holes read as holes.
 */
@Composable
fun CalendarTab(
    month: CalendarMonth,
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit = {},
) {
    val spacing = SakuTheme.spacing

    Column(modifier = modifier.fillMaxSize().padding(horizontal = spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_HEADINGS.forEach { heading ->
                Text(
                    text = heading,
                    style = SakuTheme.typography.caption,
                    color = SakuTheme.colors.ink3,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(vertical = spacing.sm),
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            // Clears the floating add button, which otherwise sits on the 30th and 31st.
            contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
        ) {
            // Blanks push the 1st under its weekday. They carry no date and are not
            // clickable, so they cannot be mistaken for days of this month.
            items(month.leadingBlanks) { Box(Modifier.aspectRatio(1f)) }

            items(month.cells, key = { it.date.toString() }) { cell ->
                DayCell(cell = cell, onClick = { onDayClick(cell.date) })
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell, onClick: () -> Unit) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing
    val net = cell.net
    val description = cell.date.dayHeaderLabel() +
        if (net == null) ", nothing recorded" else ", net ${formatIdrSigned(net)}"

    Column(
        modifier = Modifier
            .aspectRatio(1f)
            // Seven columns on a 360dp screen give a 45dp cell, under the 48dp target
            // the rest of the app holds to.
            .sizeIn(minWidth = spacing.touchTarget, minHeight = spacing.touchTarget)
            // Only a day with rows is a target: tapping an empty day would switch to
            // Daily and scroll to a section that does not exist.
            .then(
                if (cell.hasTransactions) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        // Otherwise TalkBack reads "14, -5m" with no month and no hint
                        // that the cell does anything.
                        .semantics { contentDescription = description }
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            )
            .padding(spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = SakuTheme.typography.caption,
            color = if (cell.hasTransactions) colors.ink else colors.ink3,
            maxLines = 1,
        )
        if (net != null) {
            Text(
                text = compactNet(net),
                style = SakuTheme.typography.caption,
                color = netColor(net),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun netColor(net: Money) = when {
    net.isPositive -> SakuTheme.colors.income
    net.isNegative -> SakuTheme.colors.expense
    else -> SakuTheme.colors.ink3
}

/**
 * A cell is about a seventh of the screen wide, so `Rp 5.061.400` cannot fit. Rounds
 * to the nearest thousand or million, which is enough for a glance; the exact figure
 * is one tap away in the Daily tab.
 */
internal fun compactNet(net: Money): String {
    val rupiah = net.rupiah
    if (rupiah == 0L) return "0"
    // Rounded, not truncated. 1.999.000 truncates to "1m", which reads as half the
    // value on a cell whose whole job is a glance.
    val thousands = roundedDiv(rupiah, 1_000)
    if (thousands == 0L) return if (rupiah > 0) "<1k" else "-<1k"
    val millions = roundedDiv(thousands, 1_000)
    return if (millions != 0L) "${millions}m" else "${thousands}k"
}

private fun roundedDiv(value: Long, by: Long): Long {
    val half = by / 2
    return if (value < 0) (value - half) / by else (value + half) / by
}

/** Room for the floating add button, which is 56dp plus its 16dp margin. */
private val FAB_CLEARANCE = 88.dp
