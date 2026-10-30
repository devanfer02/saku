package com.dvnnfrr.saku.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dvnnfrr.saku.domain.MonthView
import com.dvnnfrr.saku.domain.PeriodSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeriodStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun store() = PeriodStore(context)

    @Test
    fun `defaults to no pay cycle and calendar months`() {
        assertEquals(PeriodSettings(startDay = null, view = MonthView.CALENDAR), store().current())
    }

    @Test
    fun `the start day and the view survive a new store instance`() {
        store().apply {
            setStartDay(30)
            setView(MonthView.PAY)
        }

        val reopened = store().current()
        assertEquals(30, reopened.startDay)
        assertEquals(MonthView.PAY, reopened.view)
        assertEquals(30, reopened.activeStartDay)
    }

    @Test
    fun `clearing the start day hides the switch and keeps the view for later`() {
        val store = store()
        store.setStartDay(25)
        store.setView(MonthView.PAY)

        store.setStartDay(null)

        assertNull(store.settings.value.startDay)
        assertNull(store.settings.value.switchView)
        assertNull(store.settings.value.activeStartDay)
        assertEquals(MonthView.PAY, store().current().view)
    }

    @Test
    fun `the flow carries a write at once`() {
        val store = store()

        store.setStartDay(5)

        assertEquals(5, store.settings.value.startDay)
        assertEquals(MonthView.CALENDAR, store.settings.value.switchView)
    }

    @Test
    fun `unreadable stored values fall back rather than throwing`() {
        context.getSharedPreferences("saku_period", Context.MODE_PRIVATE)
            .edit()
            .putInt("pay_start_day", 40)
            .putString("month_view", "FORTNIGHT")
            .commit()

        assertEquals(PeriodSettings(), store().current())
    }
}
