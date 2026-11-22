# 37: Home: a short welcome

**What to build:** The home page of the guide, `docs/guide/index.md`. It replaces the placeholder. It lists the six pains in the reader's words, says in one sentence what LVH Yard does, and links to Start.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## The page covers

- One sentence about what LVH Yard does, taken from the first lines of the README.
- The six pains of the spec, as a list. Each pain links to the page that fixes it. A pain whose page is not live yet has no link.
- A link to "What LVH Yard solves" and one to "Install LVH Yard".

## Verification

No command to run. Check every claim against `README.md` and `GLOSSARY.md`.

## Acceptance

- [x] The page leaves the placeholder text. It is `docs/guide/index.md`.
- [x] The maintainer and the writer draft it together.
- [x] The page was drafted with `technical-writing` (saved in `.scratch/guide-site/drafts/37-home.stage1.md`, which Git ignores) and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve`.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The first sentence is the second line of `README.md`: "Reach any development service on your HomeLab by name, not by IP and port." The second sentence follows the README text about the hostname `http://grafana.lvh.me`. The page shows no command, no output and no test statement.
- The six pains are in the list without links and without answers. "What LVH Yard solves" holds the answers, so each fact has one home. The ticket asked for a link on each pain whose page is live. Four of the six pains have no recipe yet, so a link on each would send three of them to the same page. The page instead has an ordered list "Start here" with the four published pages of the Start section, in the order of the navigation.
- The header field of the Base Domain script shows `lvh.me` on this page, and the script turns `http://grafana.lvh.me` into a link.
- When the maintainer approves the page, remove `index.md` from `draft_docs`. All four linked pages are published, so the strict build keeps passing.
