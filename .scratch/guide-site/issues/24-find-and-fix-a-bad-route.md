# 24: Recipe 14: Find and fix a bad Route

**What to build:** Write the recipe "Recipe 14: Find and fix a bad Route" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"The hostname does not work, and I do not know why."

## Result

You name the cause from a Service Warning, a Reachability test, the Route Diagram, or the access log.

## Commands to use

- `lvh-yard catalog --details` for the state and the Service Warnings.
- `lvh-yard test <service>` for Reachability.
- `lvh-yard publish <service> --access-log on` and `lvh-yard ws compose logs traefik`.
- `lvh-yard status`.
- The Route Diagram of the dashboard, as a content tab.

## Diagram idea

A decision diagram that starts at "the hostname fails": the name resolves? the state? the Instance? the warning?

## Verification

`mise run dev:up`. Break a Service on purpose in four ways (no Instance, a critical check, an Out-of-range Instance, a bad Route Profile) and paste the real output. Read the user guide sections "Service Warnings" and "Reachability tests".

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
