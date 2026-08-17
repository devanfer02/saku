package com.dvnnfrr.saku.data

import android.content.Context
import android.content.SharedPreferences
import com.dvnnfrr.saku.domain.ThemePreference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Where the appearance choice lives.
 *
 * Deliberately not a Room table. The theme has to resolve before the first frame,
 * and reaching into the database on the main thread to decide what colour to paint
 * is the wrong shape — [SharedPreferences] is already memory-resident by then. It is
 * also the only setting the app has, so a settings table would be one row forever.
 */
interface AppearanceStore {
    val preference: Flow<ThemePreference>

    /** The value available synchronously, for the first composition. */
    fun current(): ThemePreference

    fun set(preference: ThemePreference)
}

class SharedPreferencesAppearanceStore(context: Context) : AppearanceStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun current(): ThemePreference = read()

    /**
     * Emits on every write, including one from another process. Conflated because the
     * consumer only ever wants the latest value — a burst of writes should repaint
     * once, not once per tap.
     */
    override val preference: Flow<ThemePreference> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    override fun set(preference: ThemePreference) {
        prefs.edit().putString(KEY, preference.name).apply()
    }

    /**
     * An unrecognised stored value falls back rather than throwing. A downgrade after
     * a future release added a mode would otherwise crash on launch, with no way to
     * reach the setting that caused it.
     */
    private fun read(): ThemePreference {
        val stored = prefs.getString(KEY, null) ?: return ThemePreference.FOLLOW_SYSTEM
        return ThemePreference.entries.firstOrNull { it.name == stored }
            ?: ThemePreference.FOLLOW_SYSTEM
    }

    private companion object {
        const val FILE = "saku_appearance"
        const val KEY = "theme_preference"
    }
}
