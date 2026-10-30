## 1. Domain and data

- [ ] 1.1 Add `domain/Reminder.kt`: `ReminderSchedule` (enabled, day, time; default off, Sunday, 20:00), `nextReminderAt(now, schedule, zone)`, `ReminderNudge` and `reminderNudge(lastEntry, now, zone)`, with unit tests for later-today, exactly-now, earlier-today, other days, yesterday, today, never
- [ ] 1.2 Add `TxnDao.lastEnteredAtEpochSec()` and `TransactionRepository.lastEnteredAt()`; Robolectric test for hand-entered, imported and adjustment rows
- [ ] 1.3 Add `data/ReminderStore.kt` (SharedPreferences, flow plus `current()` and `set`) with tests for defaults, persistence and an unrecognised stored day

## 2. System glue

- [ ] 2.1 Allow `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` in `CheckNoNetworkPermission` and `NoNetworkPermissionTest`; declare both in the manifest
- [ ] 2.2 Add `reminder/ReminderScheduler` (`sync`, `cancel`, `canNotify`), `ReminderReceiver` (post and re-arm), `ReminderRescheduleReceiver` (boot, time, zone, update) and the notifier with its channel, strings and icon
- [ ] 2.3 Wire `reminders` and `reminderScheduler` into `AppContainer`; re-arm on application start
- [ ] 2.4 Run `checkDebugNoNetworkPermission` and read the merged manifest

## 3. Setting UI

- [ ] 3.1 Add `ui/reminder/ReminderAssembly.kt` (`ReminderUiState`, `assembleReminder`) with tests
- [ ] 3.2 Add `ReminderViewModel` (setEnabled, setDay, setTime, recheck) with Robolectric tests that read the scheduled alarm
- [ ] 3.3 Add `ReminderDialog` (stateless and stateful): switch, day options, time picker, permission request, blocked line with a settings button, next-reminder line
- [ ] 3.4 Add `MoreEntry.REMINDER` under Appearance and host the dialog in `MoreScreen`

## 4. Tap target

- [ ] 4.1 `MainActivity` reads the open-entry extra on a fresh launch and in `onNewIntent`; `SakuApp`, `SakuShell` and `SakuNavHost` pass the one-shot flag to Trans; `TransactionsScreen` opens the sheet on a new transaction and consumes it

## 5. Verify

- [ ] 5.1 `:app:testDebugUnitTest :app:assembleDebug` green
