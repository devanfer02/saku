package com.dvnnfrr.saku.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dvnnfrr.saku.SakuApplication
import com.dvnnfrr.saku.domain.reminderNudge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * The weekly alarm. Re-arms next week first, so a failure while reading the ledger or
 * posting cannot end the schedule, then posts the reminder worded from the ledger.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val container = (context.applicationContext as SakuApplication).container
        val schedule = container.reminders.current()
        container.reminderScheduler.sync(schedule)
        if (!schedule.enabled) return

        // The ledger read is a database query, so it leaves the main thread; goAsync
        // keeps the process alive until it finishes.
        val pending = goAsync()
        receiverScope.launch {
            try {
                val last = container.transactions.lastEnteredAt()
                val nudge = reminderNudge(last, Clock.System.now(), TimeZone.currentSystemDefault())
                showReminder(context.applicationContext, nudge)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Re-arms the stored reminder after a restart, a clock or zone change, or an app
 * update, none of which keep an AlarmManager alarm where it was.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val container = (context.applicationContext as SakuApplication).container
        container.reminderScheduler.sync(container.reminders.current())
    }
}

private val RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
)

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
