package com.dvnnfrr.saku.ui.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodMode
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.LocalDate

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * Names a range in words.
 *
 * The month names are a hardcoded list rather than a `DateTimeFormatter`, for the same
 * reason `formatIdr` groups digits by hand: a device set to another locale would
 * otherwise relabel the app's own chrome.
 */
fun periodLabel(range: DateRange): String = when (range.mode) {
    PeriodMode.MONTHLY -> "${monthName(range.start)} ${range.start.year}"
    PeriodMode.ANNUALLY -> range.start.year.toString()
    PeriodMode.WEEKLY, PeriodMode.CUSTOM -> spanLabel(range.start, range.endInclusive)
}

/**
 * Names a range inside a sentence, where the header above already names the year:
 * `Spent in October`, `21% less than September`. A month keeps its year only when it
 * differs from [reference]'s, so December against January reads `December 2025`.
 * Other modes have no shorter honest name and read as [periodLabel].
 */
fun periodName(range: DateRange, reference: DateRange = range): String =
    if (range.mode == PeriodMode.MONTHLY && range.start.year == reference.start.year) {
        monthName(range.start)
    } else {
        periodLabel(range)
    }

private fun monthName(date: LocalDate): String = MONTH_NAMES[date.monthNumber - 1]

private fun shortMonth(date: LocalDate): String = monthName(date).take(3)

private fun spanLabel(start: LocalDate, end: LocalDate): String = when {
    start == end -> "${start.dayOfMonth} ${shortMonth(start)} ${start.year}"
    start.year != end.year ->
        "${start.dayOfMonth} ${shortMonth(start)} ${start.year} – " +
            "${end.dayOfMonth} ${shortMonth(end)} ${end.year}"
    start.monthNumber != end.monthNumber ->
        "${start.dayOfMonth} ${shortMonth(start)} – " +
            "${end.dayOfMonth} ${shortMonth(end)} ${end.year}"
    else -> "${start.dayOfMonth} – ${end.dayOfMonth} ${shortMonth(end)} ${end.year}"
}

/**
 * The period header: step back, the range in words, step forward, and an optional
 * trailing slot for whatever the host screen wants beside it: a mode selector on stats,
 * nothing on the transaction list.
 *
 * With [onLabelClick] set, the label is a button that opens the host's period picker: it
 * gains a drop-down arrow, and [labelClickDescription] names the action for TalkBack,
 * which still reads the period itself first. Null keeps the label plain text.
 */
@Composable
fun PeriodBar(
    range: DateRange,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = periodLabel(range),
    previousDescription: String = "Previous period",
    nextDescription: String = "Next period",
    trailing: (@Composable () -> Unit)? = null,
    onLabelClick: (() -> Unit)? = null,
    labelClickDescription: String? = null,
) {
    val colors = SakuTheme.colors
    val spacing = SakuTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = spacing.rowMinHeight)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousDescription,
                tint = colors.ink2,
            )
        }
        if (onLabelClick == null) {
            Text(
                text = label,
                style = SakuTheme.typography.heading,
                color = colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = spacing.touchTarget)
                    .clip(RoundedCornerShape(spacing.corner))
                    .clickable(
                        onClickLabel = labelClickDescription,
                        role = Role.Button,
                        onClick = onLabelClick,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = SakuTheme.typography.heading,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.ink2,
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = nextDescription,
                tint = colors.ink2,
            )
        }
        trailing?.let {
            Row(
                modifier = Modifier.padding(start = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                it()
            }
        }
    }
}

@SakuPreview
@Composable
private fun PeriodBarPreview() {
    SakuPreviewSurface(padded = false) {
        Column(modifier = Modifier.width(360.dp)) {
            PeriodBar(
                range = DateRange.ofMonth(LocalDate(2026, 8, 17)),
                onPrevious = {},
                onNext = {},
            )
            PeriodBar(
                range = DateRange.ofWeek(LocalDate(2026, 8, 17)),
                onPrevious = {},
                onNext = {},
                onLabelClick = {},
                labelClickDescription = "Choose a week",
            )
            PeriodBar(
                range = DateRange.ofYear(2026),
                onPrevious = {},
                onNext = {},
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SakuTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}
