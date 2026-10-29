## 1. Domain

- [ ] 1.1 Add `DateRange.ofPayMonth(anyDay, startDay)` with clamping to the month's last day and a shift that steps one pay month
- [ ] 1.2 Majority-month label function with the tie rule pinned by a test
- [ ] 1.3 Exhaustive test: every D from 1 to 31 over 2025 to 2027, each day in exactly one pay month, adjacency, and D = 1 equal to calendar months; plus the spec's named scenarios

## 2. Settings

- [ ] 2.1 `PeriodStore` on SharedPreferences beside `AppearanceStore`: start day (nullable) and selected view, as `Flow` plus a synchronous read; wire into `AppContainer`
- [ ] 2.2 Pay cycle screen under More > Organise: pick 1 to 31 or clear, preview current and previous pay months

## 3. Screens

- [ ] 3.1 Calendar month / Pay month switch beside the month selector on Trans and Stats, hidden when no start day is set
- [ ] 3.2 Trans month navigation and Monthly view month rows build pay months when selected
- [ ] 3.3 Stats Monthly mode, the six-month strip and the comparison line build pay months when selected
- [ ] 3.4 Span line under the label in Pay month
- [ ] 3.5 ViewModel tests: switch shared across tabs, persisted, hidden when unset; filter unaffected

## 4. Verification

- [ ] 4.1 Emulator pass with start day 30 against the imported October 2026 data, both themes
- [ ] 4.2 `ANDROID_HOME=/home/dvnnfrr/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` green
