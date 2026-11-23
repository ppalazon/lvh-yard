# 31: Advanced: How the Workstation Folder wires Consul, Traefik and the dashboard

**What to build:** One long page for the reader who wants to know how the parts fit, with diagrams of the interoperability, the configuration of Consul and Traefik, and the reason for each choice.

**Blocked by:** 01

**Status:** done

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

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/advanced/compose-consul-traefik.md`, under "Advanced" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real. See "Draft notes".
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve`.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Sources read on the day of writing: `config/compose.yaml`, `config/consul.sh`, `config/traefik.sh`, `config/00-lvh-yard.hcl`, `config/10-tls.hcl`, the ADRs 0001, 0002, 0011 and 0012, `SECURITY.md`, and the sections "Running on a Workstation", "Joining a Yard", "Workstation CA and certificates" and "Service Warnings" of the user guide. The files that `ws init` wrote in a test folder matched `config/`.
- Tested on throwaway Workstations in `/tmp` (folders `rc31w` and `hand`, so their Compose projects have their own volumes): a Foreign Instance with the Consul HTTP API (`PUT /v1/agent/service/register`, answer `200`) and with `docker exec <consul container> consul services register` (`Registered service: foreign2`). The catalog listed both as `unpublished` `1/1`, `catalog --details` printed the Foreign Instance and its `risk: [Foreign Instance]` line, `publish foreign` made the Route answer `200`, the catalog marked the source `[foreign]`, `lvh-yard remove foreign --port 4000` and `--id foreign-1` refused, and `PUT /v1/agent/service/deregister/<id>` removed both (the published Service stayed, `unavailable`, `Empty`). The page uses the default Consul port 8500, and the test used 18500.
- Tested: a Workstation without `ws init`. The files of `config/` (`compose.yaml`, `00-lvh-yard.hcl`, `consul.sh`, `traefik.sh`, `lvh-yard.yaml`), the empty folders `tls/consul`, `tls/traefik`, `traefik/dynamic` and `backups`, and an `.env` with `YARD_WORKSTATION`, `YARD_GOSSIP_KEY` (from `openssl rand -base64 32`), `YARD_BIND_ADDRESS`, `YARD_UID`, `YARD_GID`, `YARD_DOCKER_GID`, `COMPOSE_PROFILES=dashboard,traefik` and other ports than the defaults. `docker compose up -d` started the three containers, and `lvh-yard status` printed `ok` for Consul and Traefik. `lvh-yard ws show` and `ws set` worked on that folder, with no layout marker file.
- Not tested: `ws compose upgrade` or `ws yard init` on a folder that was written by hand. The sentence "What you lose" lists only what `ws init` does and a by-hand folder lacks: the commented `.env`, the Gossip Key, the modes and the checks. It does not say what the commands refuse.
- Read in the files, not tested: that Consul uses `YARD_BIND_ADDRESS` for its RPC and gossip ports and `client_addr` for HTTP, DNS and gRPC (the Consul rule for `-bind` and `client_addr`), and that an empty `YARD_BIND_ADDRESS` makes the script use the default-route address.
- The section "Run on a public server (not tested)" has the approved text of the spec and its facts. The spec bullet about the recipe "Use a basic-auth Route Profile" is left out, because that recipe is not written. Nobody has tested a public server.
- The diagrams: a flowchart of the containers and their files (the first version had crossing arrows, so each container holds its own files now) and a sequence diagram of the Routes.

## Review round

- The maintainer pointed out that `ws compose upgrade` writes the scripts again, so LVH Yard owns them. The table says `LVH Yard` for `scripts/consul.sh` and `scripts/traefik.sh`, and the text says why. ADR-0011 calls the scripts an exception because `ws init` writes them without a notice and they are meant to be edited. The page follows the rule of the upgrade.
- The container name is explained: `lvh-yard ws compose start` runs Compose in the Workstation Folder, Compose names the project after the folder, the container is `<project>-consul-1`, and the default folder gives `lvh-yard-consul-1`. The catalog listing has its own paragraph.
- The removal of a Foreign Instance has its own paragraphs: why `lvh-yard remove` refuses (the exact message came from the earlier test: `The Service foreign has no Static Instance foreign-1 on this Workstation.`), how to deregister it, and what stays. The last sentence, `lvh-yard service delete foreign`, follows the user guide and was not run after the deregistration.

## Second review round: a page of its own

- The maintainer asked for the section "Register a service in Consul yourself" as an advanced topic by itself. It is now `docs/guide/advanced/register-in-consul.md`, the second page of "Advanced", with the three sections that the maintainer gave it (`Register directly on Consul`, `Catalog response and warnings`, `Remove a Foreign Instance`) as its headings. The main page keeps one sentence that links to it. The tests of the section are in "Draft notes" above. The page is in `draft_docs` with the main page, and it is part of this ticket.
