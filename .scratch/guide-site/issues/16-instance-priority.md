# 16: Recipe 6: Instance Priority

**What to build:** Write the recipe "Recipe 6: Instance Priority" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## Pain

"I want one name to use my dev process first and the container only when the process is down."

## Result

You order the Instance Sources of a Service, and the Route uses the first one with a passing Instance.

## Commands to use

- `lvh-yard priority web`, `lvh-yard priority web <id> <id>`, `lvh-yard priority web --off`.
- `lvh-yard catalog --details` to read the IDs and the Health.

## Diagram idea

A state diagram with the Instance Sources in order and the Health of each. Reuse the landing page SVG idea.

## Verification

`mise run dev:up`. Recipe 7 uses the same commands for a Compose stack. Keep recipe 6 about the concept and the commands, and recipe 7 about the workflow.

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

- The page is `docs/guide/recipes/choose/instance-priority.md`, titled "Put your process before the container" as in the outline. It is in `draft_docs`, and `zensical.toml` has the new navigation section "Choose which Instance answers". The stage 1 text is in `drafts/16-instance-priority.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading.
- Tested on a throwaway Workstation in `/tmp` (folder `rcpw`, Workstation name `ws-a`, other ports than the defaults), with the dashboard image `ghcr.io/ppalazon/lvh-yard-dashboard:latest` and the `axllent/mailpit` container `mailpit-1` bound to the Service `mailpit`. The process was `python3 -m http.server 8025 --bind 127.0.0.1` with a page that says "my copy". Every output on the page is real, except that the Instance ID is the one of that run.
  - `register static` put the new source last, and `priority mailpit` showed the container `(in use)`. `priority mailpit <id>` moved the Static Instance first, and the hostname answered "my copy" a few seconds later.
  - Failover: the hostname answered `502` for 14 seconds after the process stopped (polled every 2 s), then the container answered. The catalog said `degraded 1/1 [docker]`. When the process started again, the hostname returned to it after 12 seconds. The page says "about 15 seconds", as the user guide does.
  - `priority mailpit --off`: the hostname alternated between "my copy" and Mailpit. The first requests after the command still went to the old Route, so the test waited about 12 seconds. `--on` restored the order. `remove mailpit --host 127.0.0.1 --port 8025` removed the Static Instance and the container kept answering (the `priority` output showed one source).
  - Dashboard: on the Service page, `Add Static Instance` opens the form `Add Static Instance` with `Host`, `Port` and `Add`. The icon button `Move Static Instance 127.0.0.1:8025 up` moved the source first (checked with `priority`). The `Instance Priority` switch exists. The page does not say that the switch works, because the test did not click it.
- Not tested: the dashboard switch `Instance Priority`, a Compose binding in place of a container binding (that is ticket 12), Yard Scope with an Alias above another source (the user guide says it is a risk, and the page leaves it out), and the `--interval` flag for a faster failover.
- After the maintainer approves the page: remove it from `draft_docs`, link it from the Instance Priority section of the landing page and from the README bullet "Instance Priority", mark the ticket done, delete the stage 1 draft, and commit.
