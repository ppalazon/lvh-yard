# 29: Concepts: Scope

**What to build:** Local Scope and Yard Scope: who gets a hostname, and who can connect.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- A table of the two Scopes and the hostname that each gives.
- The warning that Scope decides who gets a hostname, not who can connect.
- The Out-of-range Instance rule.

## Verification

Check against ADR-0003 and the glossary.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
