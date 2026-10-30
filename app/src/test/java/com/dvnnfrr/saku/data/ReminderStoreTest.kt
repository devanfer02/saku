package com.dvnnfrr.saku.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.domain.ReminderSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun store() = ReminderStore(context)

    @Test
    fun `a fresh install is off on Sunday at 20_00`() {
        assertEquals(ReminderSchedule(), store().current())
    }

    @Test
    fun `a choice survives a new store instance`() {
        val friday = ReminderSchedule(enabled = true, day = DayOfWeek.FRIDAY, time = LocalTime(7, 30))
        store().set(friday)

        assertEquals(friday, store().current())
    }

    @Test
    fun `the flow opens on the stored value`() = runTest {
        val monday = ReminderSchedule(enabled = true, day = DayOfWeek.MONDAY)
        store().set(monday)

        assertEquals(monday, store().schedule.first())
    }

    @Test
    fun `unrecognised stored values fall back rather than throwing`() {
        context.getSharedPreferences("saku_reminder", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", true)
            .putString("day", "FUNDAY")
            .putInt("minute_of_day", 99_999)
            .commit()

        assertEquals(ReminderSchedule(enabled = true), store().current())
    }
}
