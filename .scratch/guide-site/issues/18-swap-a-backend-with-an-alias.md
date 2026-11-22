# 18: Recipe 10: Swap a backend with an Alias

**What to build:** Write the recipe "Recipe 10: Swap a backend with an Alias" with the recipe template of the spec.

**Blocked by:** 01, 17

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"I want my local `web` to use the `api` that runs on a coworker's Workstation, and go back later."

## Result

An Alias adds the Instances of a Remote Service to your Service. You remove it to go back.

## Commands to use

- `lvh-yard alias add api --workstation ws-b --service api`, `lvh-yard alias list api`, `lvh-yard alias remove api --workstation ws-b --service api`.
- `lvh-yard priority api` to order the Alias against a Static Instance.

## Diagram idea

A flow diagram: the Route of `api` on ws-a uses the Instances of `api` on ws-b.

## Verification

`mise run dev:up`. Read the glossary entries for Alias and Remote Service first, and the Out-of-range rule: an Alias to a loopback Instance does not route.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
