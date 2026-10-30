<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call, returning the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely. Indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Workflow

Rules, not suggestions. Architecture rules live in the root `CLAUDE.md`; this file is how you work, not what you build.

### Commits

**One sentence. No body.** `<type>(<domain>): <subject>`

Types: `feat` · `fix` · `refactor` · `test` · `chore` · `doc`. Domain is the area, not a directory path: `transactions`, `entry`, `stats`, `accounts`, `categories`, `excel`, `nav`, `theme`, `data`, `ci`, `claude`. `doc` takes no domain.

```
feat(stats): add the subcategory drill-down
fix(accounts): keep a back-dated transfer on both legs
refactor(nav): resolve a pushed route back to its parent tab
test(excel): cover the transfer rows with no destination
chore(ci): sign the release build from repository secrets
doc: write the architecture rules down
```

Imperative mood, no trailing period, under ~72 characters. If the subject needs an "and", it is two commits. Reasoning that does not fit belongs in the OpenSpec design doc or a code comment, where it stays next to the thing it explains, not in a commit body nobody greps.

**No em dashes.** Not in commit messages, not in PR titles or bodies, not in code comments, not in anything written for this repo. Use a comma, a full stop, or parentheses. This applies to prose written in chat too.

Always commit `openspec/`. It is the versioned "why".

### When a change needs OpenSpec first

**Large** means any one of:

- bootstrapping a new domain or package
- a refactor that moves or renames anything with more than a couple of call sites
- UI work that scatters, meaning more than one feature directory, or adding a screen
- anything that changes a repository interface, a Room entity, or a migration

Any of those: `/opsx:propose` **before** writing code, then `/opsx:apply` task by task. Small and well-scoped work (a bug fix, a string, one function) goes straight to code.

The workflow is `/opsx:explore` (unknowns) → `/opsx:propose` (proposal, design, specs, tasks) → `/opsx:apply` (implement, ticking `tasks.md` as you go) → `/opsx:sync` → `/opsx:archive`. `openspec/changes/` is in flight; `openspec/specs/` is synced capability. Audit with `openspec list` and `openspec status --change <name>`.

Decisions made while building go in the change's `design.md`, including the ones that were rejected and why. That file is the record of what was considered, not a summary of what shipped.

### Branching and pull requests

**Never commit a feature to `master`.** One branch per feature, PR into `master`.

```
feat/<slug>      new capability          feat/budget-caps
fix/<slug>       bug fix                 fix/transfer-opening-date
refactor/<slug>  no behaviour change     refactor/assembly-extraction
chore/<slug>     build, CI, tooling      chore/release-signing
```

Then:

1. Push the branch and open the PR against `master`.
2. **Run `/humanizer` over the title and body before posting.** Default model prose is a tell; the PR is read by a person.
3. Comment `@copilot review` on the PR.
4. Address the review, then merge.

Do not merge your own PR before the review comment has been posted and answered.

CI has two lanes. A pull request into `master` runs unit tests, lint, the debug build and the no-network-permission gate. A push to `master` builds the signed release and uploads exactly one artifact, `saku-release-apk`. Maestro is not in CI (decided 30 Oct 2026); run the flows locally against the emulator.

### Before calling a session done

```bash
ANDROID_HOME=/home/dvnnfrr/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Both green, every time, including for a change with no test of its own. A worktree has no `local.properties`, which is why `ANDROID_HOME` is set inline.

A test that passes both before and after a fix proves nothing. When fixing a bug, confirm the new test fails against the old behaviour before keeping it.
