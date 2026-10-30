package com.dvnnfrr.saku.data

import android.content.Context
import android.content.SharedPreferences
import com.dvnnfrr.saku.domain.ChartStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Where the Stats chart style lives, so Stats opens in the style it was left in.
 *
 * The same shape as [SharedPreferencesAppearanceStore] and for the same reason: one
 * value, needed on the first frame of the screen, is not worth a Room table. A class
 * rather than an interface and an implementation, because nothing asks for a second
 * one; the ViewModel test uses this class on Robolectric's preferences.
 */
class ChartStyleStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Emits the stored style, then every write. Conflated: only the latest matters. */
    val style: Flow<ChartStyle> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    fun current(): ChartStyle = read()

    fun set(style: ChartStyle) {
        prefs.edit().putString(KEY, style.name).apply()
    }

    /** An unrecognised stored value falls back to bars rather than throwing. */
    private fun read(): ChartStyle {
        val stored = prefs.getString(KEY, null) ?: return ChartStyle.BARS
        return ChartStyle.entries.firstOrNull { it.name == stored } ?: ChartStyle.BARS
    }

    private companion object {
        const val FILE = "saku_stats"
        const val KEY = "chart_style"
    }
}
