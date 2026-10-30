package com.dvnnfrr.saku.ui.reminder

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.data.ReminderStore
import com.dvnnfrr.saku.domain.ReminderSchedule
import com.dvnnfrr.saku.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The setting against a real store and a real scheduler, reading back the alarm
 * Robolectric's AlarmManager holds, so "what the dialog shows" and "what will fire"
 * are checked together.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = TimeZone.of("Asia/Jakarta")

    // Friday 30 Oct 2026, 10:00.
    private val clock = object : Clock {
        override fun now(): Instant = LocalDateTime(2026, 10, 30, 10, 0).toInstant(zone)
    }

    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): ReminderViewModel = ReminderViewModel(
        store = ReminderStore(context),
        scheduler = ReminderScheduler(context, clock) { zone },
        clock = clock,
        zone = zone,
    )

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime(2026, 11, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    @Test
    fun `a fresh install is off and arms nothing`() {
        val state = viewModel().state.value

        assertFalse(state.enabled)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `turning it on arms the coming Sunday at 20_00`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.setEnabled(true)

        assertEquals(at(1, 20), alarms.peekNextScheduledAlarm()?.triggerAtMs)
        assertEquals(NextReminder("Sun", "1 Nov 2026", "20:00"), vm.state.value.next)
    }

    @Test
    fun `changing the day and time re-arms a single alarm`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.setEnabled(true)
        vm.setDay(DayOfWeek.WEDNESDAY)
        vm.setTime(LocalTime(7, 30))

        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(at(4, 7, 30), alarms.peekNextScheduledAlarm()?.triggerAtMs)
        assertEquals(
            ReminderSchedule(enabled = true, day = DayOfWeek.WEDNESDAY, time = LocalTime(7, 30)),
            ReminderStore(context).current(),
        )
    }

    @Test
    fun `turning it off cancels the alarm`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.setEnabled(true)
        vm.setEnabled(false)

        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertNull(vm.state.value.next)
    }

    @Test
    fun `blocked notifications keep the choice and show the warning until allowed`() = runTest {
        notifications.setNotificationsEnabled(false)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.setEnabled(true)

        assertTrue(vm.state.value.enabled)
        assertTrue(vm.state.value.blocked)

        notifications.setNotificationsEnabled(true)
        vm.recheck()

        assertFalse(vm.state.first().blocked)
    }
}
