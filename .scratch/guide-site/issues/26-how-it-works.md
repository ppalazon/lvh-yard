# 26: Concepts: How it works

**What to build:** Explain a request from the browser to the Instance, for a reader who does not know Consul or Traefik.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- One sequence diagram: DNS, Traefik, the Route, the Instance.
- Where the setup lives: the Workstation Folder, Consul, the dashboard.
- No Consul or Traefik vocabulary beyond one sentence each. The Advanced page holds the rest.

## Verification

Check against ADR-0001, ADR-0002 and ADR-0012.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/concepts/how-it-works.md`, titled "How a request reaches your Service". It is in `draft_docs`, and `zensical.toml` has a new group "Concepts" with the label "How it works", between "Recipes" and "Advanced". The stage 1 text is in `drafts/26-how-it-works.stage1.md`. `ste_check.py` reports no findings. The page has one sequence diagram (DNS, Browser, Traefik, Dashboard, Consul, Instance), four steps, a section "Where the setup lives", and "See it yourself". It names Consul and Traefik in one sentence each and links to the Advanced page.
- Checked against ADR-0001 (one Consul per Workstation, federated: "the Consuls of a Yard share their Services"), ADR-0002 (Traefik reads its Routes from the dashboard of the same Workstation through the HTTP provider, and keeps its last configuration when a fetch fails) and ADR-0012 (the three containers keep host networking, so Traefik reaches loopback, Docker networks and the LAN).
- Tested on a throwaway Workstation in `/tmp` (folder `rchww`, Workstation name `ws-a`, other ports than the defaults) with the Service `web` (Static Instance `127.0.0.1:4000`). The outputs on the page are real.
  - The Traefik container ran with `--providers.http.endpoint=http://127.0.0.1:<dashboard port>/traefik/configuration` and `--providers.http.pollInterval=5s`, and the three containers had the network mode `host`.
  - `curl http://127.0.0.1:<dashboard port>/traefik/configuration` returned the Routes. The page shows the router `web` (rule ``Host(`web.lvh.me`)``, entry point `web`) and the service `web` (server `http://127.0.0.1:4000`), shortened and formatted. The real answer has more routers (the three default Services, and a second router `web__https` for each) and one line of JSON.
  - `curl http://127.0.0.1:<consul port>/v1/kv/lvh-yard/?keys` listed `lvh-yard/first-setup`, and `lvh-yard/publications/<name>` and `lvh-yard/services/<name>` for `consul-ui`, `traefik-admin`, `web` and `yard`. The Consul catalog listed the Instance `web` at `127.0.0.1:4000` with passing checks.
  - With the dashboard container stopped, `web.lvh.me` still answered `200` (12 seconds after the stop), and it answered `200` after the restart.
  - The Workstation Folder had `.env`, `.lvh-yard-layout`, `backups/`, `compose.yaml`, `consul.d/`, `lvh-yard.yaml`, `scripts/`, `tls/` and `traefik/`. The page names all but `.lvh-yard-layout`.
- The page shows the ports 8500 and 7780 (the defaults), and the test used other ports. The page does not claim anything about the Yard sharing beyond ADR-0001.
- Not checked: the effect on new Services while the dashboard is stopped (the page says that they get no hostname, from ADR-0002), and the exact list of what `consul.d/`, `traefik/` and `scripts/` hold (the page says "files of Consul and Traefik that LVH Yard writes").
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
