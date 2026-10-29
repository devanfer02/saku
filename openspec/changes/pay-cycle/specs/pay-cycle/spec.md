## ADDED Requirements

### Requirement: Pay cycle setting
The app SHALL let the user set the day of the month on which a pay month starts, from 1 to 31, under More > Organise > Pay cycle. The setting SHALL be unset by default. The setting screen SHALL preview the current and previous pay months as date spans.

#### Scenario: Setting payday to the 30th
- **WHEN** the user sets the pay cycle start to 30 on 30 Oct 2026
- **THEN** the preview shows the current pay month as 30 Oct 2026 to 29 Nov 2026 and the previous one as 30 Sep 2026 to 29 Oct 2026

#### Scenario: Clearing the setting
- **WHEN** the user clears the pay cycle
- **THEN** the month view switch disappears and every screen uses calendar months

### Requirement: Two month views
When a pay cycle is set, Trans and Stats SHALL offer a switch between Calendar month and Pay month next to the month selector. Calendar month SHALL be the default. The choice SHALL be shared by Trans and Stats and SHALL persist across restarts. When no pay cycle is set, the switch SHALL NOT be shown.

#### Scenario: Switching on Stats
- **WHEN** the user switches Stats to Pay month
- **THEN** Trans also shows pay months the next time it is opened

#### Scenario: Restart
- **WHEN** the app is restarted with Pay month selected
- **THEN** it opens in Pay month

### Requirement: Pay month arithmetic
With start day D, a pay month SHALL run from day D of one month to the day before day D of the next month, inclusive. Where a month has fewer than D days, day D SHALL be that month's last day. Stepping previous and next SHALL move one pay month and never skip or overlap days. A start day of 1 SHALL produce exactly the calendar months.

#### Scenario: Start day 25
- **WHEN** D is 25 and the date is 30 Oct 2026
- **THEN** the pay month is 25 Oct 2026 to 24 Nov 2026

#### Scenario: Start day 31 across February
- **WHEN** D is 31 and the user steps from the pay month that starts 31 Jan 2026
- **THEN** that month ends 27 Feb 2026, the next starts 28 Feb 2026 and ends 30 Mar 2026, and the one after starts 31 Mar 2026

#### Scenario: Every day belongs to exactly one pay month
- **WHEN** any D from 1 to 31 is applied to every day from 1 Jan 2025 to 31 Dec 2027
- **THEN** each day falls in exactly one pay month and consecutive pay months are adjacent

### Requirement: Pay month labels
A pay month SHALL be labelled with the calendar month that holds most of its days, and its exact date span SHALL always be shown beneath the label.

#### Scenario: Start day 25
- **WHEN** the pay month is 25 Oct 2026 to 24 Nov 2026
- **THEN** it is labelled "November 2026" with "25 Oct to 24 Nov 2026" beneath

#### Scenario: Start day 5
- **WHEN** the pay month is 5 Oct 2026 to 4 Nov 2026
- **THEN** it is labelled "October 2026" with "5 Oct to 4 Nov 2026" beneath

### Requirement: Screens that follow the view
While Pay month is selected, Trans month navigation, the Trans Monthly view's month rows, Stats Monthly mode, the six-month strip and the previous-period comparison SHALL all use pay months. Weekly, Annually and custom Period modes SHALL be unaffected. Totals, the transaction filter and search SHALL behave identically in both views apart from the date range.

#### Scenario: Six-month strip in Pay month
- **WHEN** D is 30 and Stats is in Monthly mode with Pay month selected
- **THEN** the strip's six bars are the six pay months ending with the selected one, each labelled by its majority month

#### Scenario: Comparison line
- **WHEN** Stats shows the pay month 30 Sep to 29 Oct 2026
- **THEN** "vs previous" compares it with 30 Aug to 29 Sep 2026
