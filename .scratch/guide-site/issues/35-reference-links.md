# 35: Reference: index and links to the glossary and the user guide

**What to build:** The Reference section index. It links to `GLOSSARY.md`, `docs/user-guide.md`, `docs/adr/`, `SECURITY.md` and the OpenAPI file on GitHub, and says what each one is. It says that `docs/user-guide.md` is an AI-written reference.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- One line for each linked document.
- The links use the `main` branch on GitHub.

## Verification

- Every link answers 200.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Page: `docs/guide/reference/links.md`, in `draft_docs`, nav label "Documents" in the Reference group. The title is "More documents", as in the outline.
- Two tables. The first lists the five documents of the ticket: `GLOSSARY.md`, the user guide (it says that an AI wrote it), `docs/adr/`, `SECURITY.md` and `dashboard/openapi.yaml`. The second, "Get help", lists `SUPPORT.md` and `CONTRIBUTING.md`, which the ticket does not list. A closing line repeats what `SUPPORT.md` asks for in a bug report: `lvh-yard --version`, `lvh-yard status` and the operating system.
- Every link answered 200 after redirects, on the `main` branch of GitHub: the six files and the `docs/adr` folder.
- The descriptions come from the first lines of each document. The ADR line (the options that the project rejected) matches the `Considered Options` sections of the ADRs.
- Nothing to run on this page.
