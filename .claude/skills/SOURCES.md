# Third-party skills

Copied on 2026-10-30 from shallow clones. Each installed SKILL.md has a short "Saku install note" block under its frontmatter; nothing else was changed.

| Skill | Repo | Path copied | Commit | Licence |
| --- | --- | --- | --- | --- |
| `impeccable` | https://github.com/pbakaus/impeccable | `.claude/skills/impeccable/` (`SKILL.md`, `reference/`) | `02523aec3e4c4580102d996d1b6a71fbe062e03f` | Apache-2.0 (`impeccable/LICENSE`, `impeccable/NOTICE.md`; `reference/android.md` and `ios.md` derive from ehmo/platform-design-skills, MIT) |
| `better-colors` | https://github.com/jakubkrehel/skills | `skills/better-colors/` (`*.md`) | `592aedc44d7d5b3e29740984252902f3e5402a1c` | MIT, Copyright (c) 2026 Jakub Krehel (`better-colors/LICENSE`) |
| `better-writing` | https://github.com/jakubkrehel/skills | `skills/better-writing/` (`*.md`) | `592aedc44d7d5b3e29740984252902f3e5402a1c` | MIT, Copyright (c) 2026 Jakub Krehel (`better-writing/LICENSE`) |

## Left out on purpose

- `impeccable/scripts/`: a launcher that downloads and runs a prebuilt binary, a 1.1 MB font index and browser-injection JavaScript. None of it applies to a native Compose app. The skill falls back to reading files directly.
- `agents/openai.yaml` in each jakubkrehel skill: Codex metadata, unused by Claude Code.

## Not installed (evaluated)

- https://github.com/addyosmani/agent-skills (MIT, `eb07f19f52c2669f2364897aaa293e2aaddd7b1c`): `frontend-ui-engineering` is React and Tailwind; the rest are process skills.
- https://github.com/google-labs-code/design.md (Apache-2.0, `6e5da5382f41edbba9d73b2c62f33f32e9971f15`): a file format spec, not a skill. Its `.agents/skills/` are for developing that repo.
