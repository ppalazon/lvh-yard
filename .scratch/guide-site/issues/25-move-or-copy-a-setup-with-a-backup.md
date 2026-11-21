# 25: Recipe 15: Move or copy a setup with a Backup

**What to build:** Write the recipe "Recipe 15: Move or copy a setup with a Backup" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"I got a new laptop, or I want my teammate to have my setup."

## Result

You export a Backup, import it on another Workstation, and check the plan before it writes.

## Commands to use

- `lvh-yard export -o ws-a.yaml`, `lvh-yard import --dry-run ws-a.yaml`, `lvh-yard import ws-a.yaml`, `lvh-yard import --update ws-a.yaml`.

## Diagram idea

A flow diagram of what a Backup holds and what it never holds (Bound Instances, Health, Remote Services).

## Verification

`mise run dev:up`. Warn first that a Backup holds the basic-auth hashes of your Route Profiles. Read the user guide section "Backups".

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
