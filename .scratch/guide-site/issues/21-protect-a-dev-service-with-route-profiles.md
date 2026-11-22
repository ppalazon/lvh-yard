# 21: Recipe 12: Protect a dev service with Route Profiles

**What to build:** Write the recipe "Recipe 12: Protect a dev service with Route Profiles" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"I want a password, an IP allow list, or CORS headers on a service, and I do not want to touch its code."

## Result

A Route Profile adds a Traefik middleware to the Route of a Service.

## Commands to use

- `lvh-yard profiles set profiles.yaml`, `lvh-yard publish admin --profile basic-auth`, `lvh-yard profiles`, `lvh-yard profiles show`, `lvh-yard profiles edit`.
- A profile for basic auth, one for an `ipAllowList`, and one for CORS headers, in Traefik's file format.
- The dashboard as a content tab for the Route Profiles page.

## Diagram idea

A flow diagram: request, middlewares in order, Instance.

## Verification

`mise run dev:up` and `curl -i`. Read the user guide section "Route Profiles" for what the dashboard refuses.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
