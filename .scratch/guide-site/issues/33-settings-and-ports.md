# 33: Reference: Settings and ports

**What to build:** A table of the `ws set` settings, the `.env` names, the default ports, and what listens on which address.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- The setting names of `lvh-yard ws set`, with the `YARD_*` variable and the default.
- A table of ports: Traefik 80 and the HTTPS port, the dashboard 7780, the Traefik admin 8080, Consul 8500, 8600, 8503, 8300, 8301 and 8302.
- What faces the LAN and what stays on loopback.

## Verification

- Compare with `config/.env.example` and `config/compose.yaml`.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Page: `docs/guide/reference/settings-and-ports.md`, in `draft_docs`, nav label "Settings" in the Reference group.
- Compared with `config/.env.example`, `config/compose.yaml`, `config/traefik.sh`, `config/consul.sh` and the setting table of the CLI (`cli/internal/settings/settings.go`). The CLI accepts `https-port` too. The user guide list leaves it out, and the page includes it.
- Ran on a throwaway Workstation (ports shifted to 19xxx, removed afterwards):
  - `ws show` right after `ws init` printed the defaults of the page (`YARD_BIND_ADDRESS=127.0.0.1`, both bind addresses `127.0.0.1`, `YARD_HTTP_PORT=80`, `YARD_HTTPS_PORT=` empty, 8080, 7780, 8500, 8600, 8503, 8300, 8301, 8302, and `YARD_UID`, `YARD_GID`, `YARD_DOCKER_GID`, `COMPOSE_PROFILES=dashboard,traefik`).
  - `ss -ltnu` alone: every port on `127.0.0.1`, Consul 8301 and 8302 and 8600 also on UDP. After `ws yard init --address <LAN address>`: 8300, 8301 and 8302 (TCP, UDP for 8301 and 8302) on the LAN address, and 8500, 8503, 8600, admin and the Traefik port on `127.0.0.1`. With `traefik-bind-address` and `dashboard-bind-address` set to `0.0.0.0`, both listened on every address, and the admin port stayed on `127.0.0.1`.
  - `ws set http-port ...` printed `Saved .env.` and the `compose recreate` command. A value of 70000 printed `invalid http-port: use a port from 1 to 65535`.
  - `ws tls https create` wrote `YARD_HTTPS_PORT=443`.
  - `ws set traefik off` changed `YARD_TRAEFIK_ENABLED` to false and `COMPOSE_PROFILES` to `dashboard`, and printed the command that stops the Traefik container. A later `ws compose down -v` left that stopped container, so I removed it by name.
- Not tested: the listening address of the HTTPS port. The real Traefik of the maintainer holds port 443 on this computer, so the sandbox Traefik did not start with HTTPS. The page says that it follows `traefik-bind-address`, which `config/traefik.sh` shows. Windows listens differently, and the page says nothing about it, except that `instance-host` is Windows only.
- Differences with the comment in `config/.env.example`: it says that only the Consul server port and the serf WAN port face the LAN. The test showed that 8301 also listens on the LAN address. The page says that Consul listens on 8300, 8301 and 8302 there, and that the firewall rule needs only 8300 and 8302, as the Yard recipe says.
