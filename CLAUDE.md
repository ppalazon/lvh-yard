## Agent skills

### Issue tracker

Bugs and feature requests from users live in GitHub Issues. Specs and tickets for planned work live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage roles, plus `done` for finished issues. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Trail learnings

- Save a learning only for a trap, a failed attempt, or a choice whose reason the code cannot show. The commit already records what changed. When a hook asks for a learning and nothing fits, say so and stop.
- When a fact fits in a code comment beside the code, write the comment there instead.
- When a feature's spec and tickets are done, delete its folder, and replace or delete the learnings that the feature made stale. Run `trail note --replaces <old file>` first, and `git rm` the old file only after trail says that it saved the new one.

### Checks

The shell starts in the repo root: run commands from there, with relative paths. While you work, run `mise run test:unit` (unit tests only, a few seconds). Run `mise run check` before committing. After you change a dashboard component test, run `mise run test:flaky`. To try a change across Workstations, `mise run dev:up` starts two test Workstations on this computer. For a check on real Windows, read `docs/windows-test-vm.md`. See `docs/development.md`.

### Releases

Release status: `mise run release:status`. The steps are in `docs/development.md#releasing`.
