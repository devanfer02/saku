package com.dvnnfrr.saku.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.theme.SakuTheme
import com.dvnnfrr.saku.domain.DateRange
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/**
 * Start and end for a custom period.
 *
 * The picker speaks UTC millis. Converting through UTC start-of-day rather than the
 * device zone keeps the round trip exact: any offset applied on the way in has to be
 * undone on the way out, and a half-hour zone makes that fragile. The same reasoning as
 * the entry sheet's date picker; the dates chosen here are calendar boundaries, and
 * `DateRange` turns them into instants in the device's zone at query time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerDialog(
    initial: DateRange,
    onPick: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Pinned to a day-first, Monday-first English calendar rather than the device's.
    // On an en-US phone Material would otherwise start weeks on Sunday, which the
    // transaction calendar does not, and type dates as MM/DD/YYYY in input mode, where
    // the rest of the app writes the day first. Not saved across a rotation: the
    // rememberSaveable overload takes no locale in this Material version.
    val state = remember(initial) {
        DateRangePickerState(
            locale = Locale.UK,
            initialSelectedStartDateMillis = initial.start.toUtcMillis(),
            initialSelectedEndDateMillis = initial.endInclusive.toUtcMillis(),
        )
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis?.toUtcDate()
                    // A single tap selects only a start. Treating that as a one-day
                    // range beats refusing the pick, which leaves the user in a dialog
                    // with no obvious way forward.
                    val end = state.selectedEndDateMillis?.toUtcDate() ?: start
                    if (start != null && end != null) onPick(start, end)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DateRangePicker(
            state = state,
            title = { RangePickerTitle() },
            headline = { RangePickerHeadline(state) },
        )
    }
}

@Composable
private fun RangePickerTitle() {
    Text(
        text = stringResource(R.string.stats_pick_range),
        style = SakuTheme.typography.label,
        color = SakuTheme.colors.ink2,
        modifier = Modifier.padding(SakuTheme.spacing.lg),
    )
}

/**
 * The chosen span, in the words the stats screen shows under its header once the pick
 * is applied. Material's own headline is set at headline size and wraps a year-long
 * span onto two lines on a phone; this one fits on one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerHeadline(state: DateRangePickerState) {
    val label = rangePickerHeadline(
        start = state.selectedStartDateMillis?.toUtcDate(),
        end = state.selectedEndDateMillis?.toUtcDate(),
    )
    Text(
        text = label ?: stringResource(R.string.stats_pick_range_none),
        style = SakuTheme.typography.heading,
        color = SakuTheme.colors.ink,
        maxLines = 1,
        modifier = Modifier.padding(
            start = SakuTheme.spacing.lg,
            end = SakuTheme.spacing.md,
            bottom = SakuTheme.spacing.md,
        ),
    )
}

/**
 * What the headline says for a pick in progress, or null before a start is chosen. A
 * start with no end reads as that one day, because that is what Done applies.
 */
internal fun rangePickerHeadline(start: LocalDate?, end: LocalDate?): String? {
    if (start == null) return null
    val last = end?.takeIf { it >= start } ?: start
    return explicitRangeLabel(DateRange.custom(start, last))
}

internal fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun Long.toUtcDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
