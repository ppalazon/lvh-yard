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

While you work, run `mise run test:unit` (unit tests only, a few seconds). Run `mise run check` before committing. The pre-commit hook (`mise run hooks`) also scans for secrets with gitleaks. To try a change across Workstations, `mise run dev:up` starts two test Workstations on this computer. See `docs/development.md`.

### Docs

When a change affects how people install or use LVH Yard, update `docs/user-guide.md`. When it affects building, testing or releasing, update `docs/development.md`. `README.md` and the landing page in `docs/site/` are short: they link to the user guide, and do not repeat it. Update them when a change alters what LVH Yard is or how it installs. The maintainer reviews them.

### Writing

Write all text in simple English: CLI help, messages, page text, API docs, docs, code comments and configuration comments. Use short sentences, active voice, and the condition before the command.

- CLI and page text says only what a thing does, in one short line.
- A code comment says only what the code cannot: why, a hidden rule, or a value the type does not show.
- CLI text and dashboard text may name `docker compose` and `lvh-yard` commands, for example to point at `lvh-yard ws compose logs consul`. Neither names `mise` tasks, because Workstations run LVH Yard without this repo.
