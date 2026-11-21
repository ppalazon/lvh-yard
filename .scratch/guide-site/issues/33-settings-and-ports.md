# 33: Reference: Settings and ports

**What to build:** A table of the `ws set` settings, the `.env` names, the default ports, and what listens on which address.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- The setting names of `lvh-yard ws set`, with the `YARD_*` variable and the default.
- A table of ports: Traefik 80 and the HTTPS port, the dashboard 7780, the Traefik admin 8080, Consul 8500, 8600, 8503, 8300, 8301 and 8302.
- What faces the LAN and what stays on loopback.

## Verification

- Compare with `config/.env.example` and `config/compose.yaml`.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
