# 16: Recipe 6: Instance Priority

**What to build:** Write the recipe "Recipe 6: Instance Priority" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"I want one name to use my dev process first and the container only when the process is down."

## Result

You order the Instance Sources of a Service, and the Route uses the first one with a passing Instance.

## Commands to use

- `lvh-yard priority web`, `lvh-yard priority web <id> <id>`, `lvh-yard priority web --off`.
- `lvh-yard catalog --details` to read the IDs and the Health.

## Diagram idea

A state diagram with the Instance Sources in order and the Health of each. Reuse the landing page SVG idea.

## Verification

`mise run dev:up`. Recipe 7 uses the same commands for a Compose stack. Keep recipe 6 about the concept and the commands, and recipe 7 about the workflow.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
