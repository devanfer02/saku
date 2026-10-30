package com.dvnnfrr.saku.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Whether first-run setup was completed or skipped on this install.
 *
 * The same shape as [ReminderStore] and [ChartStyleStore] for the same reasons: one
 * value, read before the first frame, is not worth a Room table. A class with no
 * interface, because nothing asks for a second implementation.
 *
 * Not carried in a backup. A restored phone has transactions, and the gate already
 * treats any transaction as "in use", so the flag would add nothing there.
 */
class FirstRunStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    /** One way only: nothing in the app offers setup again once it is done. */
    fun markComplete() {
        prefs.edit().putBoolean(KEY_COMPLETE, true).apply()
    }

    private companion object {
        const val FILE = "saku_first_run"
        const val KEY_COMPLETE = "complete"
    }
}
