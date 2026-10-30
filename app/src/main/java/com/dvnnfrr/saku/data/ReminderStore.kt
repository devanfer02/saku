package com.dvnnfrr.saku.data

import android.content.Context
import android.content.SharedPreferences
import com.dvnnfrr.saku.domain.ReminderSchedule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * Where the weekly reminder's settings live.
 *
 * The same shape as [ChartStyleStore] for the same reasons: a handful of values, read
 * by a boot receiver before any screen exists, are not worth a Room table. A class with
 * no interface, because nothing asks for a second implementation.
 */
class ReminderStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Emits the stored schedule, then every write. Conflated: only the latest matters. */
    val schedule: Flow<ReminderSchedule> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in KEYS) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun current(): ReminderSchedule = read()

    fun set(schedule: ReminderSchedule) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putString(KEY_DAY, schedule.day.name)
            .putInt(KEY_MINUTE, schedule.time.hour * MINUTES_PER_HOUR + schedule.time.minute)
            .apply()
    }

    /**
     * Anything missing or unrecognised falls back to the default rather than throwing:
     * the boot receiver reads this with no screen to recover from a crash on.
     */
    private fun read(): ReminderSchedule {
        val defaults = ReminderSchedule()
        val day = prefs.getString(KEY_DAY, null)
            ?.let { stored -> DayOfWeek.entries.firstOrNull { it.name == stored } }
            ?: defaults.day
        val minute = prefs.getInt(KEY_MINUTE, -1)
        val time = if (minute in 0 until MINUTES_PER_DAY) {
            LocalTime(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
        } else {
            defaults.time
        }
        return ReminderSchedule(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            day = day,
            time = time,
        )
    }

    private companion object {
        const val FILE = "saku_reminder"
        const val KEY_ENABLED = "enabled"
        const val KEY_DAY = "day"
        const val KEY_MINUTE = "minute_of_day"
        val KEYS = setOf(KEY_ENABLED, KEY_DAY, KEY_MINUTE)
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
