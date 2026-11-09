## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage roles, plus `done` for finished issues. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Checks

While you work, run `mise run test:unit` (unit tests only, a few seconds). Run `mise run check` before committing. To try a change across Workstations, `mise run dev:up` starts two test Workstations on this computer. See `docs/development.md`.

### Docs

When a change affects how people install or use LVH Yard, update `docs/user-guide.md`. When it affects building, testing or releasing, update `docs/development.md`. The maintainer writes `README.md` by hand, so leave it to them.

### Writing

Write all text in simple English: CLI help, messages, page text, API docs, docs, code comments and configuration comments. Use short sentences, active voice, and the condition before the command.

- CLI and page text says only what a thing does, in one short line.
- A code comment says only what the code cannot: why, a hidden rule, or a value the type does not show.
- CLI text names `docker compose` and `lvh-yard` commands. Dashboard text names only HTTP addresses. Neither names `mise` tasks, because Workstations run LVH Yard without this repo.
