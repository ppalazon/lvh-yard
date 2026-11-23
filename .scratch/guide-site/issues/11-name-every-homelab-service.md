# 11: Recipe 2: Name every HomeLab service

**What to build:** Write the recipe "Recipe 2: Name every HomeLab service" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Pain

"I have a NAS, a media server, a Grafana container and a dev process, and I remember none of the ports."

## Result

Each of them has a name, and `lvh-yard catalog` lists them all.

## Commands to use

- `lvh-yard service create`, `register static` (with `--path /health`, `--interval`, `--tag`, `--meta`), `register container`, `publish`.
- `lvh-yard catalog --details` for the Instance addresses and the Health.
- `lvh-yard containers` to find the containers that no Service follows.

## Diagram idea

One diagram: three kinds of Instance Source (a Static Instance on the NAS, a Container Binding, a Static Instance on loopback) under three Services.

## Verification

`mise run dev:up`, with one real container and two listeners.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/name/homelab-services.md`, the first page of "Name your services" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` (the outline is in `.scratch/guide-site/drafts/11-homelab-services.stage1.md`, which Git ignores) and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve`.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup: a throwaway Workstation in `/tmp` with other ports than the defaults. The "NAS" and the "media server" were two `python3 -m http.server` listeners on `0.0.0.0` (ports 5000 and 8096) of this computer, reached at its LAN address `192.168.1.67`, because no NAS is available. The container was `docker run -d --name grafana -p 3000 grafana/grafana` (the image was pulled for this test). The page uses the same addresses as the tests, so every output is real. The default HTTP port is not used by the test, so the `curl` checks used the test port. The page tells the reader to open the hostnames in the browser. The test Workstation, the listeners and the container are removed.
- `lvh-yard containers` listed `grafana: 1 container; 3000; Unfollowed: grafana` in the group "Other containers". The page shows that group only.
- All three Services were `healthy` in `lvh-yard catalog`, and `curl` through Traefik gave `200` for `nas` and `media`, and `302` for `grafana` (the redirect of Grafana to its login page).
- `--tag` and `--meta` show on the page of the Service in the dashboard only, so the CLI steps leave them out. They stay in the user guide.
- Failures reproduced on the real Service names: a stopped listener gave `Connection refused`; the address `192.168.1.250` gave `No answer within 2 s: a firewall may drop the connection.` in about 2 seconds; an HTTP check on a listener that answers `302` on every path gave `unhealthy` and `0/1`, while `lvh-yard test` printed `ok` lines and `Every check passed`. The Instance is `critical` in `catalog --details`. The same device with the default TCP check is `healthy`. The page shows the redirecting device at port 5002, because that is the listener of the test.
- The sentence "Traefik calls each Instance over plain HTTP" comes from the dashboard text under the switch "Mutual TLS to the Instances". A device that serves only HTTPS needs the setting `Mutual TLS to the Instances` or another route, and the page does not cover it.
- The dashboard labels were checked on a running dashboard in the recipe 13 round: `Catalog`, `New Service`, `Containers`, `Create Service`, `Add Static Instance`, `Bind container`, `Published`.
- A removed container named `grafana` could have existed before the test: `docker rm -f grafana` ran before `docker run`, and its output was hidden. The image `grafana/grafana` was not on this computer before, so a container from it could not exist.
