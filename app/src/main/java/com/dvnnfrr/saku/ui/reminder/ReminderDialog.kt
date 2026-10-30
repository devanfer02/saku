package com.dvnnfrr.saku.ui.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.ui.entry.TimePickerSheet
import com.dvnnfrr.saku.ui.theme.SakuPreview
import com.dvnnfrr.saku.ui.theme.SakuPreviewSurface
import com.dvnnfrr.saku.ui.theme.SakuTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The reminder setting, over the More menu like Appearance: every choice applies at
 * once, so the only button is Done.
 *
 * Turning it on asks for the notification permission on Android 13 and later. The
 * choice is kept whatever the answer; if notifications stay blocked the dialog says so
 * and offers the system page where they can be allowed. Coming back from that page
 * re-reads the permission, so the warning clears without reopening the dialog.
 */
@Composable
fun ReminderDialog(
    onDismiss: () -> Unit,
    viewModel: ReminderViewModel = viewModel(factory = ReminderViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.recheck()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.recheck() }

    ReminderDialog(
        state = state,
        onEnabledChange = { enabled ->
            viewModel.setEnabled(enabled)
            if (enabled && context.needsNotificationPermission()) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onDayChange = viewModel::setDay,
        onTimeChange = viewModel::setTime,
        onOpenSettings = { context.startActivity(notificationSettings(context)) },
        onDismiss = onDismiss,
    )
}

private fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/** Saku's own notification page; API 26+, which is the app's floor. */
private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

@Composable
fun ReminderDialog(
    state: ReminderUiState,
    onEnabledChange: (Boolean) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.more_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.md)) {
                EnabledRow(state.enabled, onEnabledChange)
                if (state.blocked) BlockedLine(onOpenSettings)
                DayChoice(state.day, onDayChange)
                TimeRow(state.timeLabel, onClick = { pickingTime = true })
                state.next?.takeIf { !state.blocked }?.let { next ->
                    Text(
                        text = stringResource(R.string.reminder_next, next.day, next.date, next.time),
                        style = SakuTheme.typography.caption,
                        color = SakuTheme.colors.ink2,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )

    if (pickingTime) {
        // The picker takes a date and time; only the time is read back.
        TimePickerSheet(
            initial = LocalDateTime(PICKER_DATE, state.time),
            onPick = { hour, minute -> onTimeChange(LocalTime(hour, minute)) },
            onDismiss = { pickingTime = false },
        )
    }
}

private val PICKER_DATE = LocalDate(2026, 1, 1)

@Composable
private fun EnabledRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SakuTheme.spacing.touchTarget)
            // Toggleable on the row so the label is part of the target and TalkBack
            // reads label and state as one switch.
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reminder_switch),
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun BlockedLine(onOpenSettings: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.reminder_blocked),
            style = SakuTheme.typography.body,
            color = SakuTheme.colors.warning,
        )
        TextButton(onClick = onOpenSettings) {
            Text(
                text = stringResource(R.string.reminder_open_settings),
                style = SakuTheme.typography.bodyStrong,
                color = SakuTheme.colors.accent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChoice(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.reminder_day),
            style = SakuTheme.typography.label,
            color = SakuTheme.colors.ink2,
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(SakuTheme.spacing.xs),
        ) {
            REMINDER_DAYS.forEach { day ->
                FilterChip(
                    selected = day == selected,
                    onClick = { onSelect(day) },
                    label = { Text(reminderDayLabel(day)) },
                )
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reminder_time),
            style = SakuTheme.typography.label,
            color = SakuTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) {
            Text(
                text = label,
                style = SakuTheme.typography.bodyStrong,
                color = SakuTheme.colors.accent,
                modifier = Modifier.padding(horizontal = SakuTheme.spacing.xs),
            )
        }
    }
}

@SakuPreview
@Composable
private fun ReminderDialogPreview() {
    SakuPreviewSurface {
        ReminderDialog(
            state = ReminderUiState(
                enabled = true,
                next = NextReminder("Sun", "1 Nov 2026", "20:00"),
            ),
            onEnabledChange = {},
            onDayChange = {},
            onTimeChange = {},
            onOpenSettings = {},
            onDismiss = {},
        )
    }
}

@SakuPreview
@Composable
private fun ReminderBlockedPreview() {
    SakuPreviewSurface {
        ReminderDialog(
            state = ReminderUiState(enabled = true, blocked = true),
            onEnabledChange = {},
            onDayChange = {},
            onTimeChange = {},
            onOpenSettings = {},
            onDismiss = {},
        )
    }
}
