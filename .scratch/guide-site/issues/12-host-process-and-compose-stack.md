# 12: Recipe 7: One service on your Workstation, the rest in Docker

**What to build:** Write the recipe "Recipe 7: One service on your Workstation, the rest in Docker" with the recipe template of the spec.

**Blocked by:** 01, 13, 16

**Status:** done

**Core for v0.2.0:** no

## Pain

"I work on `web` as a process on my computer with hot reload, and the rest of the Compose stack runs in Docker. I want one name for `web`, and the container as the fallback."

## Result

`web.<Base Domain>` goes to your process while it runs, and to the container when you stop it.

## Commands to use

- `lvh-yard register compose shop/web --port 80`, `lvh-yard register static web --port 3000`.
- `lvh-yard priority web` and `lvh-yard priority web <id> <id>` for the order, and `--off` for the shared Route.
- `lvh-yard catalog --details` to read the IDs.

## Diagram idea

A flow diagram with two states: the process passes its health check (Route uses it), the process stops (Route uses the container).

## Verification

`mise run dev:up` with a Compose project. Watch the switch with the process stopped and started.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/recipes/name/host-process-and-compose.md`, titled "Develop one service on your Workstation, keep the rest in Docker" as in the outline. It is in `draft_docs`, and the navigation lists it in "Name your services", after "Stop chasing Compose ports". The stage 1 text is in `drafts/12-host-process-and-compose.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The page links to ticket 16 (Put your process before the container) for the failover and for the first symptom, and does not repeat them.
- Tested on a throwaway Workstation in `/tmp` (folder `rcsw`, Workstation name `ws-a`, other ports than the defaults) and a Compose project `shop` with `web` and `api` (`traefik/whoami`) and `mailpit` (`axllent/mailpit`), each with a port and no Host Port. The process was `python3 -m http.server 4000 --bind 127.0.0.1` with a page that says "my copy". The outputs on the page are real, except that the Instance ID is the one of that run.
  - `register compose` for the three services, `publish`, and each hostname answered from its container. `register static web --host 127.0.0.1 --port 4000` put the source last, and `priority web <id>` put it first: `web` answered "my copy", the catalog said `[static]`, and `api` and `mailpit` stayed `[compose]`.
  - `docker compose stop web`: `web` still answered "my copy". With the process stopped too, the hostname answered `502`, and the catalog said `unhealthy 0/1 [static]`, with the Static Instance `critical` and `routed`. `docker compose start web` moved the Route to the container (`degraded 1/1 [compose]`) within 16 seconds, and starting the process moved it back.
  - `docker compose down` then `up -d`: the three hostnames answered again after 18 seconds, with new containers, and `web` still opened the process.
  - Dashboard: `Containers` has the link `Create Service for shop/api port 80`. It opens the form `Create Service` (`Service name`, `Container Port`, `Publish` checked, `Scope` Local), and the button `Create Service` made `api` with a Compose binding to `shop/api` and a working hostname. The step that adds the Static Instance and moves it first is the one that the page of ticket 16 tested.
- Not tested: a real dev server with hot reload (the stand-in has none), a Compose stack that has a service with several containers (ticket 13 covers `--scale`), and Yard Scope for this stack.
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
