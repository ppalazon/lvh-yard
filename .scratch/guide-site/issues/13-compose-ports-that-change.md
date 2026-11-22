# 13: Recipe 8: Compose ports that change on every `compose up`

**What to build:** Write the recipe "Recipe 8: Compose ports that change on every `compose up`" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Pain

"Docker gives my container a new host port after every `docker compose up`."

## Result

You bind the Compose service once. The name stays the same, whatever port Docker gives.

## Commands to use

- `lvh-yard containers` and `lvh-yard containers --details`, to see the Container Ports and Host Ports.
- `lvh-yard register compose <project>/<service> --port <container port>`, then `publish`.
- `docker compose down && docker compose up -d`, then `lvh-yard catalog`, to prove that the name still works.

## Diagram idea

A sequence diagram: `compose up`, Docker maps a new Host Port, the dashboard follows the container, Traefik keeps the same hostname.

## Verification

`mise run dev:up` with a real Compose project. Show two `compose up` runs with two different Host Ports. Mention the unpublished-port Service Warning and the Windows behavior (user guide "Published ports on Windows").

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/name/compose-ports.md`, under "Recipes", "Name your services" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram. The title and the pain come first, the Mermaid diagram follows, and the parts "Before you start", "Steps", "Check", "Undo" and the two troubleshooting headings come after it.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` (saved in `.scratch/guide-site/drafts/13-compose-ports.stage1.md`, which Git ignores) and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. The page has no warning, because no command in it can lose data.
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve`.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup: a throwaway Workstation in `/tmp` with other ports than the defaults, because a real Workstation runs on this computer, and a Compose project `demo` with `axllent/mailpit` and `ports: ["8025"]`. `lvh-yard ws init`, `ws set` and `ws compose start` made the Workstation. The page uses the default HTTP port, so its `curl` has no port. The test `curl` used the HTTP port of the throwaway Workstation. The test Workstation and the project are removed.
- `lvh-yard containers --details` showed the group `demo` with `1025 not published, 1110 not published, 0.0.0.0:33680->8025, [::]:33680->8025`. The page shows that group only. Other groups of the real list belong to other projects of the Docker daemon.
- `register compose demo/mailpit --port 8025` with no `--service` bound the Service `mailpit` (the cleaned Compose service name). The Service must exist, so the page creates it first. With the service `mailpt`, the command stopped with `This Workstation has no Service mailpt. Create it first ...`.
- Two runs of `docker compose down` and `docker compose up -d` gave the Host Ports 33680 and 33681 (earlier runs: 33676 to 33677). `curl -sI http://mailpit.lvh.me | head -1` printed `HTTP/1.1 200 OK` after both runs. After `up`, the hostname answered `502` for 4 seconds. The Instance went from `critical` to `passing` in that time.
- `docker compose up -d --scale mailpit=2` gave `2/2` and two Instances at two Host Ports, and four `curl` calls all answered 200. `docker compose stop` gave `unavailable` and `No Instances`. `docker compose start` brought back `1/1` within 14 seconds.
- Failure with `--port 9999`: `0/1` `unhealthy`, an Instance at `172.28.0.2:9999` (the address of the container, because the port has no Host Port), and the `lvh-yard test` output on the page. A misspelled project (`dem/mailpit`) bound without an error and said `None of its containers runs now`. The catalog said `unavailable` and `No Instances`.
- `lvh-yard remove mailpit --compose-project demo --compose-service mailpit` removed the binding, and `lvh-yard service delete mailpit` removed the Service and left the container running.
- The Windows sentence comes from `docs/user-guide.md` ("Published ports on Windows"). It was not tested on Windows.
- No Service Warning showed in `catalog --details` for the Local Scope Service with the unpublished port, and the user guide says the warning belongs to Yard Scope. So this recipe does not mention it. The recipe "Show your work to a teammate" covers it.
- Zensical needs four spaces under `1.` for the content of a numbered step, and Prettier used three. `.prettierrc.yaml` now sets `tabWidth: 4` for the Markdown of the guide, and `docs/development.md` explains it. The existing pages did not change.
- This page is the first of the group "Recipes". The nav groups "Recipes" and "Name your services" hold only this page for now.

## Review round

- The diagram said that Traefik reads the Instances from Consul. The dashboard registers the Bound Instance in Consul (`ContainerSync` and `Registrations`), Consul checks it, the dashboard builds the Routes from the Instances and their Health, and Traefik reads the Routes from the dashboard (ADR-0002). The diagram now shows that order.
- New section "The same steps in the dashboard", after the steps. The throwaway Workstation showed the flow: Containers, then Create Service on the line of port 8025 of `demo/mailpit`. The form proposed the name `mailpit`, followed every container of `demo/mailpit`, and had Publish checked and Scope on Local. Create Service opened the page of the new Service.
- Step 2 now explains the three Container Ports. A Service bound to port 1025 (not published) under Local Scope was healthy, with an Instance at `172.28.0.2:1025`, the address of the container. Port 8025 gave `127.0.0.1:<Host Port>`. The Yard Scope column comes from the user guide ("Container Bindings"). The Yard Scope warning was not tested, because the throwaway Workstation was not in a Yard.
- The Check step has a browser address, `http://mailpit.lvh.me`. The Base Domain script makes it a link.
- Page 07 did not get the browser link, because the maintainer asked for it on this page only.
- Second review round: the port table has one value or a short phrase in each cell, and the dashboard section is a bullet list with no introduction. Page 07 has the same bullet list. The spec's recipe template says both.
