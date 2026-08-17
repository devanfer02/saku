# Saku

A personal finance tracker for Android. Ad-free, offline, IDR only.

Built to replace Money Manager after a year and 1,200 transactions exposed five concrete
tracking failures — documented in [`a private analysis of the export`](a private analysis of the export).
The two that drive the design: every row recorded its account as "Card", so nothing reconciled against a real
balance; and investment transfers had no destination, so net worth was uncomputable.

## What it does

| | |
|---|---|
| **Transactions** | Day-grouped Daily list and a Calendar month grid where unlogged days read as visibly empty. Income / Expense / Transfer entry with an optional subcategory. |
| **Stats** | Current month by default; Weekly, Monthly, Annually or a custom period. Donut plus a ranked category list, with drill-through to the transactions behind any figure. |
| **Accounts** | Typed Cash / Bank / Investment / Liability, headed Spendable · Invested · Total. Balances derive from an opening balance and an as-of date; Reconcile writes a visible adjustment rather than overwriting. |
| **Excel** | Import from a Money Manager export or a Saku export, with a preview, account mapping and deduplication. Import only ever inserts. Export round-trips losslessly. |

Ad-free is structural, not a promise: the manifest declares no `INTERNET` permission, and a Gradle check over the
**merged** manifest fails the build if one appears — including from a dependency.

## Status

In development. Planning artifacts live in [`openspec/changes/money-tracker-v1/`](openspec/changes/money-tracker-v1/):
`proposal.md` for the why, `design.md` for the decisions and their alternatives, `specs/` for 43 requirements
across seven capabilities, and `tasks.md` for the build sequence.

The build runs in four phases. A sequential foundation, then a proving slice that pushes a read path and a write
path through it, then four feature tracks that run in parallel in separate worktrees, then integration.

## Building

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests, including Room DAO tests via Robolectric
./gradlew check                  # the above plus the no-network guard
```

Requires a **full JDK 17–22 with `jlink`**. A JRE or a headless OpenJDK package will fail with
`jlink executable ... does not exist` during `compileDebugJavaWithJavac` — AGP needs it to build the Android
system-modules image. On Fedora:

```bash
sudo dnf install java-21-openjdk-devel
# or point Gradle at an existing full JDK for one invocation:
JAVA_HOME=~/.jdks/openjdk-22.0.2 ./gradlew assembleDebug
```

Toolchain: Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26.

## Tests

- **Unit and Room** — JVM, via Robolectric, so the balance query and period aggregates are covered in CI where
  there is no emulator.
- **E2E** — [Maestro](https://github.com/mobile-dev-inc/Maestro) flows in `.maestro/flows/`.
  ```bash
  maestro test .maestro
  ```
  Each feature track owns its own flow file, so parallel tracks never conflict in the suite.

## CI

`.github/workflows/android.yml` runs unit tests and assembles both APKs on every push, uploading them as
artifacts. It also builds on `agent/**`, so the four parallel tracks each get an independent signal. The Maestro
job is gated to `main` and manual dispatch — an emulator boot per push would dominate the feedback loop.

Release APKs are unsigned; signing needs a keystore in repository secrets.
