# 35: Reference: index and links to the glossary and the user guide

**What to build:** The Reference section index. It links to `GLOSSARY.md`, `docs/user-guide.md`, `docs/adr/`, `SECURITY.md` and the OpenAPI file on GitHub, and says what each one is. It says that `docs/user-guide.md` is an AI-written reference.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- One line for each linked document.
- The links use the `main` branch on GitHub.

## Verification

- Every link answers 200.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
