# 28: Concepts: Instance Sources and Priority

**What to build:** The four kinds of Instance Source and the order that Instance Priority sets.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- A diagram of the four sources: Static Instance, Container Binding, Alias, Foreign Instance.
- The rule: the Route uses the first source with a passing Instance. With Priority off, every passing Instance shares the Route.
- Where recipes 6, 7, 9 and 10 apply it.

## Verification

Check against the glossary and ADR-0009.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
