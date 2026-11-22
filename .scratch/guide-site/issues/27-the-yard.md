# 27: Concepts: The Yard

**What to build:** What a Yard is, how Workstations find each other, and what Yard Scope gives.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- A diagram of two Workstations and the Peer join.
- The Gossip Key, the Invitation and the Main CA, in plain words.
- What happens when a Workstation is off (Unreachable Workstation).

## Verification

Check against the glossary and ADR-0001, ADR-0016 and ADR-0017.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
