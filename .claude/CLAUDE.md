<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call, returning the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely. Indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Workflow

How to work here. What to build lives in the root `CLAUDE.md`.

### Commits

One line, no body: `<type>(<domain>): <subject>`. Imperative, no trailing period, under ~72 characters. An "and" in the subject means two commits.

Types: `feat` `fix` `refactor` `test` `chore` `doc`. Domain is the area: `transactions`, `entry`, `stats`, `accounts`, `categories`, `search`, `excel`, `nav`, `theme`, `data`, `ci`, `claude`. `doc` takes no domain.

```
fix(accounts): keep a back-dated transfer on both legs
doc: write the architecture rules down
```

No em dashes anywhere written for this repo: commits, PRs, comments, docs. Reasoning goes in the OpenSpec `design.md` or a code comment, not a commit body. Always commit `openspec/`.

### OpenSpec first for large changes

Large means any one of: a new domain or package; a move or rename with more than a couple of call sites; UI work across more than one feature directory or a new screen; a change to a repository interface, a Room entity or a migration.

Large: `/opsx:propose` before code, then `/opsx:apply` task by task, ticking `tasks.md`. Small work (a bug fix, a string, one function) goes straight to code. Decisions made while building, rejected ones included, go in the change's `design.md`.

### Branches, PRs and CI

Never commit a feature to `master`. Branch as `feat/`, `fix/`, `refactor/` or `chore/` plus a slug, push, open the PR against `master`, run `/humanizer` over the title and body, then merge.

CI: a PR runs unit tests, lint, the debug build and the no-network-permission gate. A push to `master` builds the signed release and uploads one artifact, `saku-release-apk`. Maestro is not in CI; run it locally against the emulator.

### Done means

```bash
export ANDROID_HOME="$HOME/Android/Sdk"   # your Android SDK path
./gradlew --no-daemon \
  -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx1536m \
  :app:testDebugUnitTest :app:assembleDebug
```

Both green, even for a change with no test of its own. The flags keep the build inside a 14 GB machine; never run two builds at once, and do not raise the heap whatever a skill suggests. A git worktree has no `local.properties`, so set `ANDROID_HOME` in the environment.

A bug-fix test must fail against the old behaviour, or it proves nothing.

### Local gotchas

- If a shell hook rewrites `git` output (a token-saving proxy, for example), call the binary directly, such as `/usr/bin/git`, when exact output matters.
- Emulator: an API 35 Pixel 8 image (`Pixel_8_API_35`), started with `-memory 2048` on a low-RAM machine. Stop it with `adb emu kill`; check for it with `ps -C qemu-system-x86_64`, not `pgrep -f`, which matches its own shell.
- Skills live in `.claude/skills/`; the root `CLAUDE.md` says which to load for which request.
