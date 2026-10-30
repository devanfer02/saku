## ADDED Requirements

### Requirement: Reminder setting in More
More SHALL list a "Reminder" entry in the App group, directly under Appearance. Selecting it SHALL open a dialog over the menu with an on/off switch, the seven days of the week as single-choice options, and the time of day. The reminder SHALL be off on a fresh install, with Sunday at 20:00 as the preselected day and time. Every choice SHALL apply at once and SHALL survive a restart.

#### Scenario: Fresh install
- **WHEN** the user opens the Reminder dialog for the first time
- **THEN** the switch is off, Sunday is selected and the time reads 20:00

#### Scenario: Changing the day
- **WHEN** the reminder is on and the user selects Wednesday
- **THEN** the next reminder is scheduled for the coming Wednesday at the chosen time, and the dialog names that date

#### Scenario: Choice survives a restart
- **WHEN** the user turns the reminder on for Friday 07:30 and restarts the app
- **THEN** the dialog shows on, Friday, 07:30

### Requirement: Notification permission
Turning the reminder on SHALL request the notification permission on Android 13 and later when it has not been granted. The user's choice SHALL be kept on whatever the answer. While the reminder is on and the system blocks Saku's notifications, the dialog SHALL say that the reminder cannot show and SHALL offer to open the system notification settings for Saku.

#### Scenario: Permission granted
- **WHEN** the user turns the reminder on and allows notifications
- **THEN** the switch stays on and no warning shows

#### Scenario: Permission denied
- **WHEN** the user turns the reminder on and denies notifications
- **THEN** the switch stays on and the dialog reads that notifications are blocked, with a button to open the system settings

#### Scenario: Allowed later in system settings
- **WHEN** the user allows notifications in system settings and returns to the dialog
- **THEN** the warning is gone

### Requirement: Weekly schedule
While the reminder is on, Saku SHALL show one notification a week, at the chosen time on the chosen day in the device's time zone. When the chosen moment has already passed this week, the next one SHALL be a week later. The schedule SHALL be restored after a reboot, a change of clock or time zone, and an app update. Turning the reminder off SHALL cancel the pending notification.

#### Scenario: Later the same day
- **WHEN** it is Sunday 19:00 and the reminder is set for Sunday 20:00
- **THEN** the next reminder is today at 20:00

#### Scenario: Already passed today
- **WHEN** it is Sunday 20:00 exactly and the reminder is set for Sunday 20:00
- **THEN** the next reminder is next Sunday at 20:00

#### Scenario: After a reboot
- **WHEN** the device restarts while the reminder is on
- **THEN** the next reminder is scheduled again without opening the app

#### Scenario: Turned off
- **WHEN** the user turns the reminder off
- **THEN** no notification shows at the next chosen time

### Requirement: Notification text
The notification SHALL name how long it has been since a transaction was last entered, counted in calendar days in the device zone. A transaction typed into Saku counts from when it was saved; an imported row counts from its own timestamp, because the moment of import says nothing about the user's logging. Balance adjustments SHALL NOT count. When something was entered today the reminder SHALL still show, with a lighter line. When nothing has ever been entered, it SHALL say so.

#### Scenario: Six days since the last entry
- **WHEN** the last transaction was entered on Monday and the reminder fires on Sunday
- **THEN** the body reads "Nothing logged for 6 days. Add what you spent this week."

#### Scenario: Last entry yesterday
- **WHEN** the last transaction was entered yesterday
- **THEN** the body reads "Nothing logged since yesterday. Add what you spent this week."

#### Scenario: Logged today
- **WHEN** a transaction was entered earlier today
- **THEN** the body reads "You logged today. Anything else from this week to add?"

#### Scenario: Nothing ever logged
- **WHEN** the ledger holds no transaction
- **THEN** the body reads "Nothing logged yet. Add what you spent this week."

#### Scenario: Import does not count as logging
- **WHEN** the only rows were imported today from an export whose newest row is dated 10 days ago
- **THEN** the body reads "Nothing logged for 10 days. Add what you spent this week."

### Requirement: Tapping the notification
Tapping the notification SHALL open Saku on Trans with the entry sheet open on a new transaction, whether or not Saku was already running. The notification SHALL disappear once tapped.

#### Scenario: App not running
- **WHEN** Saku is not running and the user taps the reminder
- **THEN** Saku opens on Trans with the entry sheet open on a new transaction

#### Scenario: App open on another tab
- **WHEN** Saku is open on Stats and the user taps the reminder
- **THEN** Saku switches to Trans and opens the entry sheet

### Requirement: Permissions stay offline
The merged manifest SHALL declare only `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` outside Saku's own namespace. Any other permission, and every network permission in particular, SHALL fail the build.

#### Scenario: A dependency adds a network permission
- **WHEN** a library merges `ACCESS_NETWORK_STATE` into the manifest
- **THEN** `checkDebugNoNetworkPermission` fails the build
