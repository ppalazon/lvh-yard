# 37: Home: a short welcome

**What to build:** The home page of the guide, `docs/guide/index.md`. It replaces the placeholder. It lists the six pains in the reader's words, says in one sentence what LVH Yard does, and links to Start.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## The page covers

- One sentence about what LVH Yard does, taken from the first lines of the README.
- The six pains of the spec, as a list. Each pain links to the page that fixes it. A pain whose page is not live yet has no link.
- A link to "What LVH Yard solves" and one to "Install LVH Yard".

## Verification

No command to run. Check every claim against `README.md` and `GLOSSARY.md`.

## Acceptance

- [ ] The page leaves the placeholder text. It is in `draft_docs` until the maintainer approves it.
- [ ] The maintainer and the writer draft it together.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
