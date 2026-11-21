# 32: Reference: CLI cheat sheet

**What to build:** One page of commands grouped by task (look, create, bind, publish, order, protect, test, move). Each command has one line. It is a cheat sheet, not the full reference.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- The commands of `docs/user-guide.md`, section "Using the CLI", grouped by task.
- A link to the full reference on GitHub for each group.

## Verification

- Run `lvh-yard --help` and `lvh-yard <command> --help` and compare each command and option.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
