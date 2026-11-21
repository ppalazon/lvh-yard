# 12: Recipe 7: One service on your Workstation, the rest in Docker

**What to build:** Write the recipe "Recipe 7: One service on your Workstation, the rest in Docker" with the recipe template of the spec.

**Blocked by:** 01, 13, 16

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"I work on `web` as a process on my computer with hot reload, and the rest of the Compose stack runs in Docker. I want one name for `web`, and the container as the fallback."

## Result

`web.<Base Domain>` goes to your process while it runs, and to the container when you stop it.

## Commands to use

- `lvh-yard register compose shop/web --port 80`, `lvh-yard register static web --port 3000`.
- `lvh-yard priority web` and `lvh-yard priority web <id> <id>` for the order, and `--off` for the shared Route.
- `lvh-yard catalog --details` to read the IDs.

## Diagram idea

A flow diagram with two states: the process passes its health check (Route uses it), the process stops (Route uses the container).

## Verification

`mise run dev:up` with a Compose project. Watch the switch with the process stopped and started.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
