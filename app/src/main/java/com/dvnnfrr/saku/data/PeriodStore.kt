package com.dvnnfrr.saku.data

import android.content.Context
import android.content.SharedPreferences
import com.dvnnfrr.saku.domain.MonthView
import com.dvnnfrr.saku.domain.PeriodSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the pay cycle and the month view live.
 *
 * SharedPreferences for the same reason as [AppearanceStore]: two values, needed before
 * the first frame, so a settings table would be two rows forever. Unlike the filter this
 * is persisted. Both views show complete totals and a pay month always shows its span,
 * so reopening in Pay month cannot pass one figure off as another.
 *
 * A concrete class rather than an interface: there is one implementation, and tests run
 * it under Robolectric. It is built once in `AppContainer`, and every write goes through
 * it, so [settings] is updated in memory on write rather than by a preference listener.
 */
class PeriodStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val current = MutableStateFlow(read())

    val settings: StateFlow<PeriodSettings> = current.asStateFlow()

    /** The value available synchronously, for a ViewModel's opening range. */
    fun current(): PeriodSettings = current.value

    /** Sets the day a pay month starts, or clears the pay cycle with null. */
    fun setStartDay(day: Int?) {
        require(day == null || day in 1..31) { "Pay start day $day is not a day of the month" }
        prefs.edit().apply { if (day == null) remove(KEY_START_DAY) else putInt(KEY_START_DAY, day) }.apply()
        current.update { it.copy(startDay = day) }
    }

    fun setView(view: MonthView) {
        prefs.edit().putString(KEY_VIEW, view.name).apply()
        current.update { it.copy(view = view) }
    }

    /**
     * Anything unreadable falls back to calendar months rather than throwing, the same
     * reasoning as the appearance store: a bad stored value must not crash the launch.
     */
    private fun read(): PeriodSettings {
        val day = prefs.getInt(KEY_START_DAY, NONE).takeIf { it in 1..31 }
        val view = prefs.getString(KEY_VIEW, null)
            ?.let { stored -> MonthView.entries.firstOrNull { it.name == stored } }
            ?: MonthView.CALENDAR
        return PeriodSettings(startDay = day, view = view)
    }

    private companion object {
        const val FILE = "saku_period"
        const val KEY_START_DAY = "pay_start_day"
        const val KEY_VIEW = "month_view"
        const val NONE = 0
    }
}
