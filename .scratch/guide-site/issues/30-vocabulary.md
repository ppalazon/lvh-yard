# 30: Concepts: Vocabulary

**What to build:** The ten words that a reader needs, in the guide's own voice, with a link to the full glossary.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- Yard, Workstation, Service, Instance, Publication, Scope, Base Domain, Route, Route Profile, Workstation CA.
- Each word has one sentence, and the same word means the same thing on every page.
- The Base Domain entry may use "shared domain" and "own domain", in the guide only. Do not change `GLOSSARY.md`.

## Verification

Every sentence must agree with `GLOSSARY.md`.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Page: `docs/guide/concepts/vocabulary.md`, in `draft_docs`, nav "Vocabulary" in Concepts. A table of the ten words with a link to the page that uses each word, and a link to `GLOSSARY.md` on GitHub.
- Each sentence was compared with its entry in `GLOSSARY.md`. Cuts: Workstation drops "unless its Traefik is off" and "It is yours, a coworker's, or the team's". Route Profile and Route say less than the glossary. The Base Domain line uses "shared domain" and "own domain" in the guide only, as the ticket allows.
- Nothing to run on this page.
