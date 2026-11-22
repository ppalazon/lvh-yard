# 04: Start: What LVH Yard solves

**What to build:** The first page of the guide. It names the six pains, shows the idea in one diagram, and lists what LVH Yard does not do.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Reader's need

A new reader must see in one minute whether LVH Yard fixes their problem.

## The page covers

- The six pains of the spec, each with one sentence of result.
- One diagram: a browser, a wildcard DNS answer of `127.0.0.1`, Traefik on this Workstation, and the Instances (a process, a container, a Workstation in the Yard).
- The list "What LVH Yard does not do": it does not start processes, it is not a DNS server, and it is not for production. Source: `SECURITY.md` and the glossary.
- Links to Install and to Pick your Base Domain.

## Verification

No command to run. Check every claim against `GLOSSARY.md` and `SECURITY.md`.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/start/what-it-solves.md`, the first page under "Start" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` (saved in `.scratch/guide-site/drafts/04-what-it-solves.stage1.md`, which Git ignores) and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] The page shows no command and no output. There is nothing to run. Every claim has a source in "Draft notes".
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve` (list, diagram).
- [x] The maintainer approved the page on 2026-11-22, and it left `draft_docs`. The maintainer decided that Podman gets no ticket for now.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

Sources of the claims on the page:

- Traefik routes by hostname: `RouteBuilder.java` builds a rule ``Host(`<hostname>`)`` for each Route.
- A Container Binding follows the container, the hostname does not change with the port: `GLOSSARY.md` (Container Binding, Bound Instance), and the check of ticket 07.
- No hosts file is needed, and LVH Yard does not edit `/etc/hosts`: `README.md` ("you do not edit `/etc/hosts`"), and no code of the CLI or the dashboard writes that file.
- Cookies ignore the port: [RFC 6265, section 8.5](https://www.rfc-editor.org/rfc/rfc6265#section-8.5). A cookie without a `Domain` attribute is host-only, so two hostnames keep their cookies apart by default.
- Some OAuth services refuse a callback URL that points to your own computer: the warning of "Pick your Base Domain", which is itself unverified third-party behavior. The page names no service.
- The Workstation CA signs the HTTPS certificate, and the browser shows no warning once it trusts the CA: `docs/user-guide.md` and the HTTPS check of ticket 06 (`curl` and Chromium).
- Yard Scope gives a hostname `<service>.<workstation>.<Base Domain>` on every other Workstation: `GLOSSARY.md` (Scope) and the check of ticket 06 (`grafana.ws-b.lvh.me` on ws-a).
- LVH Yard manages Services, not processes: `GLOSSARY.md` (top). The dashboard has no login, and a Scope decides who gets a hostname, not who can connect: `SECURITY.md` and `GLOSSARY.md` (Scope). Docker is the only runtime, and Podman is not supported: `README.md`.
- The page has no link to "Install LVH Yard", because that page does not exist yet. Add the link when ticket 05 is done.
- The page does not repeat the maintainer's wording "The maintainer has not tested a public server". The rule of the spec says that a page carries no statement about what was tested, and the landing page and the Advanced page keep that sentence.

Review round of 2026-11-22:

- The sixth pain no longer names a phone. A phone cannot run a Workstation, which needs Docker with Compose, so Yard Scope does not reach it. The plan changed too: the pain list and the recipe table of the spec, row 09 of the outline, and ticket 09 (with an "Open question"). The spec lists a device that cannot run a Workstation under "Out of scope", until the maintainer decides.
- Podman: the page says that LVH Yard supports Docker only, as `README.md` does. The reason is in the trail learning `2026-11-02-podman-is-not-supported-yet-in-pablo.md`. To support Podman, a test plan must cover: the socket path of rootless Podman (`/run/user/<uid>/podman/podman.sock`, owned by the host user, mode 0600) and a user setting that can read it (`userns_mode: keep-id`, or no `user` and `group_add`); Traefik on port 80 without root (`net.ipv4.ip_unprivileged_port_start`); the Docker-compatible events API that the dashboard uses; an empty `group_add`; the Compose labels that `podman-compose` sets, which Container Bindings match; and the limit of about 108 characters for the socket path.
