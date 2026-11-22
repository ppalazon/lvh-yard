# 30: Concepts: Vocabulary

**What to build:** The ten words that a reader needs, in the guide's own voice, with a link to the full glossary.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- Yard, Workstation, Service, Instance, Publication, Scope, Base Domain, Route, Route Profile, Workstation CA.
- Each word has one sentence, and the same word means the same thing on every page.
- The Base Domain entry may use "shared domain" and "own domain", in the guide only. Do not change `GLOSSARY.md`.

## Verification

Every sentence must agree with `GLOSSARY.md`.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
