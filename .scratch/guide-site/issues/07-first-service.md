# 07: Start: Your first Service

**What to build:** Create a Service, add a Static Instance and a Container Binding, publish it, open it, and read `lvh-yard catalog`.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Reader's need

Pains 1 and 2: "I forget the port" and "Docker changes my port". The reader wants the first hostname working.

## The page covers

- `lvh-yard service create front`, `register static front --port 3000`, `publish front`, and the browser address.
- `lvh-yard register container mailpit-1 --port 8025 --service mailpit` and `publish mailpit`.
- `lvh-yard catalog`, with its real output and what each column means.
- A diagram of Service, Instances and Publication, using only the words of the glossary.
- Undo: `unpublish`, `remove`, `service delete`.

## Verification

Run every command on `mise run dev:up` and paste the real output. Use a real Docker container.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
