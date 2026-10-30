## Why

Gap 4 in `a private analysis of the export` is late and missing entries. The owner's own data shows logging stopped for 29 days (23 Dec to 20 Jan) and for 14 days (16 to 29 Sep), and October has entries on only 12 of 30 days. Nothing in Saku asks the user to come back. The owner asked for "1 week cron notif, can be a setting in config, aka the More tab."

## What Changes

- A local notification once a week, on a day and at a time the user picks (default Sunday 20:00 in the device zone). The body says how many days have passed since the last transaction was entered, or a lighter line when something was logged today.
- A "Reminder" setting in More, in the App group under Appearance: on or off, day of week, time. Off until the user turns it on.
- Turning it on asks for the notification permission on Android 13 and later. If notifications are blocked, the setting says so in one line and offers the system settings page.
- Tapping the notification opens Saku on Trans with the entry sheet open on a new transaction.
- The alarm survives a reboot, a clock change, a time-zone change and an app update.
- **BREAKING (build gate)**: the manifest now declares `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`. The merged-manifest gate and `NoNetworkPermissionTest` change from "no foreign permission" to "only these two"; every network permission still fails the build.

## Capabilities

### New Capabilities
- `logging-reminder`: the weekly reminder, its setting in More, the notification text, scheduling, and the tap target.

### Modified Capabilities
<!-- None: openspec/specs/ holds no synced capability yet. -->

## Impact

- New: `domain/Reminder.kt`, `data/ReminderStore.kt`, `reminder/` (scheduler, receivers, notifier), `ui/reminder/` (assembly, ViewModel, dialog).
- Changed: `TxnDao` and `TransactionRepository` gain `lastEnteredAt()`; `AppContainer`, `MainActivity`, `SakuApp`, `SakuShell`, `SakuNavHost`, `TransactionsScreen` (open the entry sheet on request); `ui/shell/Placeholders.kt` (More entry); `AndroidManifest.xml`; `app/build.gradle.kts` (permission gate); `NoNetworkPermissionTest`.
- No new library. AlarmManager and NotificationCompat are already on the classpath.
