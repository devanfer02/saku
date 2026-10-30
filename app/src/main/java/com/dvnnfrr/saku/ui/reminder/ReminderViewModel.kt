package com.dvnnfrr.saku.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvnnfrr.saku.data.ReminderStore
import com.dvnnfrr.saku.domain.ReminderSchedule
import com.dvnnfrr.saku.reminder.ReminderScheduler
import com.dvnnfrr.saku.sakuViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * State for the reminder setting. Holds nothing of its own: the schedule is the store's,
 * and whether notifications are allowed is the system's, re-read on [recheck] because
 * the user can change it in system settings while the dialog is in the background.
 *
 * Every change is written to the store and armed at once, so the alarm can never
 * disagree with what the dialog shows.
 */
class ReminderViewModel(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** Bumped to re-read the notification permission; carries no meaning of its own. */
    private val checks = MutableStateFlow(0)

    val state: StateFlow<ReminderUiState> =
        combine(store.schedule, checks) { schedule, _ -> assemble(schedule) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = assemble(store.current()),
            )

    fun setEnabled(enabled: Boolean) = change { it.copy(enabled = enabled) }

    fun setDay(day: DayOfWeek) = change { it.copy(day = day) }

    fun setTime(time: LocalTime) = change { it.copy(time = time) }

    /** After the permission prompt answers, and whenever the dialog comes back into view. */
    fun recheck() = checks.update { it + 1 }

    private fun change(edit: (ReminderSchedule) -> ReminderSchedule) {
        val next = edit(store.current())
        store.set(next)
        scheduler.sync(next)
    }

    private fun assemble(schedule: ReminderSchedule) =
        assembleReminder(schedule, scheduler.canNotify(), clock.now(), zone)

    companion object {
        val Factory = sakuViewModelFactory { ReminderViewModel(reminders, reminderScheduler) }
    }
}
