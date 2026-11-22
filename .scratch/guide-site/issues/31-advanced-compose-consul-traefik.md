# 31: Advanced: How the Workstation Folder wires Consul, Traefik and the dashboard

**What to build:** One long page for the reader who wants to know how the parts fit, with diagrams of the interoperability, the configuration of Consul and Traefik, and the reason for each choice.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## The page covers

- A diagram of the three containers and the files that configure them: `compose.yaml`, `.env`, `consul.d/`, `tls/`, `scripts/consul.sh`, `scripts/traefik.sh`.
- A sequence diagram of Traefik polling the dashboard (`--providers.http.endpoint=http://<dashboard>:7780/traefik/configuration`), and the dashboard reading Consul. Why not `traefik.*` tags and the Consul Catalog provider (ADR-0001, ADR-0002).
- Why host networking (ADR-0012), what each process listens on, and what faces the LAN (the Consul federation ports).
- The Consul configuration: one datacenter per Workstation, WAN federation, the Gossip Key, TLS on RPC only (`10-tls.hcl`). The generated files, and what `ws compose upgrade` overwrites (ADR-0011).
- The Traefik configuration: the fixed flags in `compose.yaml`, the flags that `traefik.sh` adds from `.env`, the optional HTTPS entry point.
- Section: "Register a service in Consul yourself". `consul services register` or the Consul HTTP API makes a Foreign Instance. LVH Yard shows and routes it, but cannot change or remove it, and a Backup does not hold it.
- Section: "Run LVH Yard without `lvh-yard ws init`". Which files to write by hand, and what you lose.
- Section: "Run on a public server (not tested)", with the approved dashboard-login text and the facts of the spec. A link to `SECURITY.md`. No hardening advice that nobody tested.

## Verification

- Read the files in `config/` and the ADRs again on the day you write.
- Run the Foreign Instance command on `dev:up` and paste the real output.
- Do not document Traefik's Consul Catalog provider as a supported option.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
