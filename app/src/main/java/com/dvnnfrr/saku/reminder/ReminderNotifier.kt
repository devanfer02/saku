package com.dvnnfrr.saku.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dvnnfrr.saku.MainActivity
import com.dvnnfrr.saku.R
import com.dvnnfrr.saku.domain.ReminderNudge

private const val CHANNEL_ID = "weekly_reminder"
private const val NOTIFICATION_ID = 1
private const val OPEN_REQUEST_CODE = 2

/** The body line for [nudge]. */
fun reminderText(resources: Resources, nudge: ReminderNudge): String = when (nudge) {
    ReminderNudge.NothingYet -> resources.getString(R.string.reminder_nothing_yet)
    ReminderNudge.LoggedToday -> resources.getString(R.string.reminder_logged_today)
    is ReminderNudge.DaysSince -> resources.getQuantityString(R.plurals.reminder_days_since, nudge.days, nudge.days)
}

/**
 * Posts the weekly reminder. Does nothing when notifications are blocked: the setting
 * already tells the user why, and there is nobody to tell from inside a receiver.
 *
 * Tapping it opens Trans with the entry sheet on a new transaction, because the point of
 * the reminder is to get something typed.
 */
fun showReminder(context: Context, nudge: ReminderNudge) {
    val manager = NotificationManagerCompat.from(context)
    val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    if (permissionMissing || !manager.areNotificationsEnabled()) return

    manager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) },
    )

    val open = PendingIntent.getActivity(
        context,
        OPEN_REQUEST_CODE,
        MainActivity.openEntryIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val body = reminderText(context.resources, nudge)
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.reminder_notification_title))
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(open)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()
    manager.notify(NOTIFICATION_ID, notification)
}
