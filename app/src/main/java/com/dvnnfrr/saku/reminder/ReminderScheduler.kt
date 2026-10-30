package com.dvnnfrr.saku.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.dvnnfrr.saku.domain.ReminderSchedule
import com.dvnnfrr.saku.domain.nextReminderAt
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Arms and cancels the one weekly alarm.
 *
 * AlarmManager rather than WorkManager: no new dependency, no extra permissions in the
 * merged manifest, and each firing re-arms at a computed wall-clock moment, so the
 * reminder never drifts off its day and time. See logging-reminder/design.md.
 *
 * A window alarm rather than an exact one, because an exact alarm needs a permission the
 * user can revoke, and a weekly nudge within [WINDOW_MS] of the chosen time is enough.
 */
class ReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.System,
    /** Read on every sync, so a zone change picked up by the reschedule receiver applies. */
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    /** Arms the next reminder for [schedule], replacing any earlier one, or cancels it when off. */
    fun sync(schedule: ReminderSchedule) {
        if (!schedule.enabled) {
            alarms.cancel(alarmIntent())
            return
        }
        val at = nextReminderAt(clock.now(), schedule, zone())
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), WINDOW_MS, alarmIntent())
    }

    /**
     * Whether a notification posted now would show. Covers both the Android 13 runtime
     * permission and a user who blocked Saku's notifications in system settings.
     */
    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_FIRE = "com.dvnnfrr.saku.reminder.FIRE"
        private const val REQUEST_CODE = 1
        internal const val WINDOW_MS = 15 * 60 * 1000L
    }
}
