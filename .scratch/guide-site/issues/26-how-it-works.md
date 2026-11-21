# 26: Concepts: How it works

**What to build:** Explain a request from the browser to the Instance, for a reader who does not know Consul or Traefik.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- One sequence diagram: DNS, Traefik, the Route, the Instance.
- Where the setup lives: the Workstation Folder, Consul, the dashboard.
- No Consul or Traefik vocabulary beyond one sentence each. The Advanced page holds the rest.

## Verification

Check against ADR-0001, ADR-0002 and ADR-0012.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
