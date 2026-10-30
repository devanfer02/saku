# Third-party skills

Copied on 2026-10-30 from shallow clones. Each installed SKILL.md has a short "Saku install note" block under its frontmatter where its advice conflicts with Saku (no note means no conflict was found); nothing else was changed. The awesome-android-agent-skills set was first copied into `.claude/.claude/skills/` and moved here unchanged apart from those notes.

| Skill | Repo | Path copied | Commit | Licence |
| --- | --- | --- | --- | --- |
| `impeccable` | https://github.com/pbakaus/impeccable | `impeccable/` (`SKILL.md`, `reference/`) | `02523aec3e4c4580102d996d1b6a71fbe062e03f` | Apache-2.0 (`impeccable/LICENSE`, `impeccable/NOTICE.md`; `reference/android.md` and `ios.md` derive from ehmo/platform-design-skills, MIT) |
| `better-colors` | https://github.com/jakubkrehel/skills | `skills/better-colors/` (`*.md`) | `592aedc44d7d5b3e29740984252902f3e5402a1c` | MIT, Copyright (c) 2026 Jakub Krehel (`better-colors/LICENSE`) |
| `better-writing` | https://github.com/jakubkrehel/skills | `skills/better-writing/` (`*.md`) | `592aedc44d7d5b3e29740984252902f3e5402a1c` | MIT, Copyright (c) 2026 Jakub Krehel (`better-writing/LICENSE`) |
| `android-accessibility` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-accessibility/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `android-architecture` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-architecture/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `android-coroutines` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-coroutines/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `android-data-layer` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-data-layer/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `android-security-scan` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-security-scan/` (`SKILL.md`, `evals/evals.json`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `android-testing` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-testing/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `android-viewmodel` | https://github.com/new-silvermoon/awesome-android-agent-skills | `android-viewmodel/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `compose-navigation` | https://github.com/new-silvermoon/awesome-android-agent-skills | `compose-navigation/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `compose-performance-audit` | https://github.com/new-silvermoon/awesome-android-agent-skills | `compose-performance-audit/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `compose-ui` | https://github.com/new-silvermoon/awesome-android-agent-skills | `compose-ui/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `gradle-build-performance` | https://github.com/new-silvermoon/awesome-android-agent-skills | `gradle-build-performance/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |
| `kotlin-concurrency-expert` | https://github.com/new-silvermoon/awesome-android-agent-skills | `kotlin-concurrency-expert/` (`SKILL.md`) | `82900eac` | Apache-2.0 (repo licence; the folder carries no licence file of its own) |

## Left out on purpose

- `impeccable/scripts/`: a launcher that downloads and runs a prebuilt binary, a 1.1 MB font index and browser-injection JavaScript. None of it applies to a native Compose app. The skill falls back to reading files directly.
- `agents/openai.yaml` in each jakubkrehel skill: Codex metadata, unused by Claude Code.

## Not installed (evaluated)

- https://github.com/addyosmani/agent-skills (MIT, `eb07f19f52c2669f2364897aaa293e2aaddd7b1c`): `frontend-ui-engineering` is React and Tailwind; the rest are process skills.
- https://github.com/google-labs-code/design.md (Apache-2.0, `6e5da5382f41edbba9d73b2c62f33f32e9971f15`): a file format spec, not a skill. Its `.agents/skills/` are for developing that repo.
- `android-retrofit` from https://github.com/new-silvermoon/awesome-android-agent-skills (Apache-2.0, `82900eac`): Saku has no INTERNET permission, and a build gate enforces that.
- `coil-compose` from https://github.com/new-silvermoon/awesome-android-agent-skills (Apache-2.0, `82900eac`): Saku loads no remote images.
- `xml-to-compose-migration` from https://github.com/new-silvermoon/awesome-android-agent-skills (Apache-2.0, `82900eac`): Saku has no XML views; the UI is Compose only.
- `atomic-commits` from https://github.com/new-silvermoon/awesome-android-agent-skills (Apache-2.0, `82900eac`): the commit rules in `.claude/CLAUDE.md` cover this.
- `android-gradle-logic` from https://github.com/new-silvermoon/awesome-android-agent-skills (Apache-2.0, `82900eac`): Saku is a single-module build with no convention plugins.
