## Context

Saku is offline by construction: no INTERNET permission, and `check<Variant>NoNetworkPermission` fails the build on any permission outside the app's namespace that reaches the merged manifest. The owner's data shows multi-week gaps in logging (29 days over New Year, 14 days in September, 12 of 30 days in October). The app has one persisted setting today, Appearance, stored by `SharedPreferencesAppearanceStore` and shown as a dialog over the More menu.

## Goals / Non-Goals

**Goals:**
- One weekly notification at a user-chosen day and time, worded from the user's real logging history.
- A setting that reads like Appearance: a dialog over More, applied on tap, no confirm step.
- Survive reboot, clock and zone changes, and app updates without the user opening Saku.
- Keep the permission gate meaningful: two named permissions allowed, everything else still fails.

**Non-Goals:**
- More than one reminder, daily reminders, or "smart" timing based on habits.
- A summary of the reminder state in the More row's subtitle (the subtitle stays static).
- Exact-to-the-minute delivery.

## Decisions

### AlarmManager plus a boot receiver, not WorkManager
WorkManager would be a new dependency, and its manifest merges `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`. `ACCESS_NETWORK_STATE` is only for network constraints and could be stripped with `tools:node="remove"`, but that leaves three more permissions to allow in the gate and a library whose own boot handling we would not control. Periodic work also cannot anchor to a wall-clock moment: a 7-day `PeriodicWorkRequest` drifts with each run's flex window and with Doze, so "Sunday 20:00" becomes "some time on Sunday or Monday" within a few weeks unless every run re-enqueues a one-time request with a computed delay, which is the AlarmManager design with more moving parts.

AlarmManager needs no dependency. Each firing computes the next moment with `nextReminderAt` and sets one alarm, so the time never drifts. The cost is a receiver for `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `MY_PACKAGE_REPLACED`, all of which are exempt from the implicit-broadcast limits.

### Inexact window alarm, not an exact alarm
`setWindow(RTC_WAKEUP, at, 15 min)` needs no permission. An exact alarm needs `SCHEDULE_EXACT_ALARM` on API 31+, which the user can revoke and Play reviews. A weekly nudge that lands within 15 minutes of 20:00 is fine. Doze can defer it further on an idle phone; on a Sunday evening the phone is rarely idle.

Rejected: `setAndAllowWhileIdle`. It fires through Doze but its delay is unbounded by API contract; the window alarm states its bound.

### Permission gate allows exactly two permissions
`CheckNoNetworkPermission` gains an allowlist of `android.permission.POST_NOTIFICATIONS` and `android.permission.RECEIVE_BOOT_COMPLETED`. Everything else outside the app namespace still fails, so a network permission arriving from any dependency is still caught. `NoNetworkPermissionTest` changes the same way: the "no permissions at all" test becomes "only the allowlisted ones", and the network check is unchanged. The task comment asked for this to be done deliberately rather than by deleting the gate.

### Notification text counts entry time, not spend time
"Days since the last entry" uses `MAX(CASE WHEN importBatchId IS NULL THEN createdAtEpochSec ELSE occurredAtEpochSec END)` over non-adjustment rows. A hand-typed row's `createdAt` is when the user logged it, which is what the reminder is about (the analysis found half of entries back-dated from memory). An imported row's `createdAt` is the import moment, which would make the reminder say "logged today" right after an import; its `occurredAt` is better, and Money Manager's timestamps are entry times anyway. Adjustments are balance corrections from reconciling, not logging, so they are excluded.

Days are counted as calendar days in the device zone between the last entry's date and today, so 23:50 yesterday is "yesterday", not "0 days".

### Lighter line when something was logged today
The reminder still shows: "You logged today. Anything else from this week to add?" Skipping it would teach the user the reminder is unreliable, and a single entry today does not mean the week is complete. The alternative, staying silent, was rejected for that reason.

### The user's choice is kept when permission is denied
Turning the switch on stores `enabled = true` before the permission answer is known. If notifications are blocked, the alarm still runs and the receiver skips posting; the dialog shows a warning with a button to the system notification settings. Whether notifications are allowed is read through `NotificationManagerCompat.areNotificationsEnabled()`, which also covers a user who blocked Saku's notifications on Android 12 and earlier. The ViewModel holds no permission state; it re-reads on resume and after the permission result. Rejected: flipping the switch back off on denial, which loses what the user asked for and leaves them nothing to fix.

### Tapping opens the entry sheet, not just Trans
The notification's purpose is to get a transaction typed, so it opens the sheet on a new transaction. `MainActivity` turns its intent extra into a one-shot flag, consumed by `TransactionsScreen` through the same `editing = NEW` path the add button uses. `SakuShell` switches to Trans first. No entry-sheet file changes. The PendingIntent uses `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP` so a running Saku receives `onNewIntent` instead of a second activity. The flag is only read on a fresh launch (`savedInstanceState == null`), so rotating does not reopen the sheet.

### Where the pieces live
- `domain/Reminder.kt`: `ReminderSchedule`, `nextReminderAt`, `ReminderNudge` and `reminderNudge`. Pure Kotlin, no copy.
- `data/ReminderStore.kt`: a class like `ChartStyleStore`, one SharedPreferences file, no interface.
- `reminder/`: the Android glue (`ReminderScheduler`, the two receivers, the notifier). A new top-level package because it is neither data nor UI: it is the app talking to the system.
- `ui/reminder/`: `ReminderAssembly.kt` (pure), `ReminderViewModel`, `ReminderDialog` with stateless and stateful overloads. More hosts the dialog as it hosts Appearance.

The ViewModel holds nothing; its state is `assembleReminder(store.schedule, scheduler.canNotify(), clock.now(), zone)` re-evaluated when the store emits or `recheck()` is called. Every write goes to the store and to `scheduler.sync`.

### Re-reading the permission on resume
The dialog calls `recheck()` on `ON_RESUME` through `LifecycleEventEffect`, from `lifecycle-runtime-compose`. That artifact was already on the classpath through Compose UI and Navigation; it is now declared in the version catalog so the import does not lean on a transitive dependency. It adds nothing to the merged manifest (checked). Rejected: re-reading only when the dialog opens, which leaves the warning up after the user allowed notifications and came back.

### Reusing the entry sheet's time picker
The time row opens `TimePickerSheet` from `ui/entry/WhenPicker.kt` instead of a second picker. It takes a `LocalDateTime`, so the dialog passes a fixed date and reads back only the hour and minute. `clockLabel()` joins the date labels in `ui/transactions/DateLabels.kt`, so "20:00" has one formatter.

### Two receivers
`ReminderReceiver` is not exported and handles only the alarm. `ReminderRescheduleReceiver` is exported because it listens for system broadcasts; all it does is re-run `sync` from the stored schedule, so a forged intent can do nothing but reschedule the same alarm. Keeping the alarm receiver unexported means no other app can make Saku post a notification.

## Risks / Trade-offs

- [OEM battery savers kill alarms for apps that are rarely opened] → Nothing in-app can fix this; the reminder still re-arms on next launch because `SakuApplication` calls `sync` on start.
- [Doze delays the window alarm past 15 minutes] → Accepted for a weekly nudge.
- [Untested on hardware: Robolectric covers scheduling and the store, not delivery] → Listed in "check on device".
- [Two more permissions in the manifest] → Both are local; the gate still names them one by one.

## Migration Plan

None. A fresh setting, off by default; nothing to migrate.

## Open Questions

- Whether the More row should show "Sun 20:00" or "Off" as its subtitle. Left static for now.
