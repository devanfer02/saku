package com.dvnnfrr.saku.ui.entry

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.transactions.dayHeaderLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * When the transaction happened.
 *
 * Editable, not just defaulted. Half of a year of reference data was logged
 * from memory days after the fact — one stretch twenty days long — so an entry sheet
 * that can only record "now" would reproduce the gap it is meant to close.
 */
fun LocalDateTime.whenLabel(): String =
    "${date.dayHeaderLabel()}  ${hour.pad()}:${minute.pad()}"

private fun Int.pad(): String = toString().padStart(2, '0')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initial: LocalDateTime,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker speaks UTC millis. Converting through UTC start-of-day rather than
    // the device zone keeps the round trip exact: any offset applied on the way in
    // has to be undone on the way out, and a half-hour zone makes that fragile.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.date
            .atStartOfDayIn(TimeZone.UTC)
            .toEpochMilliseconds(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(
                            kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.UTC).date,
                        )
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    initial: LocalDateTime,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                com.dvnnfrr.saku.ui.theme.SakuTheme.spacing.corner,
            ),
            color = com.dvnnfrr.saku.ui.theme.SakuTheme.colors.surface,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .padding(com.dvnnfrr.saku.ui.theme.SakuTheme.spacing.lg),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                TimePicker(state = state)
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            onPick(state.hour, state.minute)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }
}
