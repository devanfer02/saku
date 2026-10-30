# Contributing to Saku

Thanks for looking. Saku is small and opinionated, so read this before you open a big pull request.

## Setup

- A full JDK 17 to 22, with `jlink`. A JRE or a headless OpenJDK fails during
  `compileDebugJavaWithJavac`. On Fedora: `sudo dnf install java-21-openjdk-devel`.
- The Android SDK, with `ANDROID_HOME` set or a `local.properties` pointing at it.
- Optional: [Maestro](https://github.com/mobile-dev-inc/Maestro) and an emulator for the flows in
  `.maestro/flows/`.

## Build and test

Your change is done when this passes:

```bash
./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs=-Xmx1536m :app:testDebugUnitTest :app:assembleDebug
```

The flags keep Gradle within reach of a 14 GB laptop. Plain `./gradlew testDebugUnitTest
assembleDebug` works too if you have the memory.

If you fix a bug, add a test that fails without your fix.

## How the code is laid out

[`CLAUDE.md`](CLAUDE.md) has the package map and the architecture rules: strict MVVM, a pure
`*Assembly.kt` adapter at every boundary, money as a `Long` of rupiah, colour literals only in
`ui/theme/`, no network permission. A change that breaks one of those rules gets sent back even if
it compiles.

## Commits and pull requests

- Branch from `master` as `feat/`, `fix/`, `refactor/` or `chore/` plus a short slug.
- One-line commit messages: `<type>(<domain>): <subject>`, for example
  `fix(accounts): keep a back-dated transfer on both legs`. Types are `feat`, `fix`, `refactor`,
  `test`, `chore` and `doc`.
- No em dashes in commits, comments or docs.
- Open the pull request against `master`. CI runs unit tests, lint, the debug build and the
  no-network check.

## Bigger changes go through OpenSpec

A new screen, a new package, a Room entity or migration, or a change to a repository interface starts
as an [OpenSpec](https://github.com/Fission-AI/OpenSpec) change: `/opsx:propose` before code, then
`/opsx:apply` task by task. Past changes are in `openspec/changes/archive/` and the current behaviour
is in `openspec/specs/`.

## Using Claude Code

Most of Saku was written with Claude Code, and the repo is set up for it. `CLAUDE.md` says which
skill to load for which kind of work, and the skills themselves ship in `.claude/skills/` with their
sources in [`SOURCES.md`](.claude/skills/SOURCES.md). You do not need Claude Code to contribute.

## Test data

Never commit real financial data. Tests and Maestro use `data/mock-money-manager.xlsx`, a mock
export with invented rows.
