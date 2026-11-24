# 17: Recipe 9: Replicas, health checks and failover

**What to build:** Write the recipe "Recipe 9: Replicas, health checks and failover" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## Pain

"I want to see what my frontend does when one of two backends dies."

## Result

The Service is Degraded while one Instance fails, and the Route uses the passing one.

## Commands to use

- `lvh-yard register static back --port 4001 --path /health` and `--port 4002`.
- `lvh-yard catalog --details`, `lvh-yard test back`.
- Stop one backend, then read the Degraded state.

## Diagram idea

A sequence diagram of a request while Instance 2 fails its check.

## Verification

`mise run dev:up` with two listeners that you stop and start. Name the time that a check needs to turn critical (`--interval` and `--timeout`).

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

- The page is `docs/guide/recipes/choose/replicas-and-failover.md`, titled "Test replicas and failover". It is in `draft_docs`, and the navigation lists it in "Choose which Instance answers", between "Put your process before the container" and "Swap a backend with an Alias". The stage 1 text is in `drafts/17-replicas-and-failover.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The diagram is a sequence of a request while Instance 2 fails its check.
- Tested on a throwaway Workstation in `/tmp` (folder `rcrfw`, Workstation name `ws-a`, other ports than the defaults) with two Python servers on 4001 and 4002, each with a page that prints `replica 1` or `replica 2` and a file `health`. The outputs on the page are real.
  - `register static back --host 127.0.0.1 --port 4001 --path /health --interval 5s --timeout 1s` (and 4002) registered the Instances. `--path` alone made an HTTP check: with a server that answered 404 on `/health`, the Instance was `critical`.
  - With the Instance Priority on, six requests all answered `replica 1`. With `priority back --off`, they alternated (`replica 2 replica 1 ...`), and `catalog` said `2/2`. `test back` said `The Instance Priority is off. The Route uses every passing Instance, ...`.
  - Stop the first server (Instance Priority off, interval 5 s, timeout 1 s): for 6 seconds (polled every second, four requests each), requests answered `200 502 200 502`, then `200 200 200 200`. `catalog` said `degraded` and `1/2`, `catalog --details` marked 4001 `critical` and 4002 `passing ... routed`, and `test` printed `FAIL Instance 127.0.0.1:4001 (not in the Route now): Connection refused` and `1 of 4 checks failed.` Starting the server again, the answers alternated again after about 6 seconds, and `catalog` said `2/2`.
  - Both servers stopped: the hostname answered `502`, `catalog` said `unhealthy` and `0/2`, and `test` printed four FAIL lines.
  - An open port with `/health` answering 404: `catalog` said `degraded` `1/2`, the Instance was `critical`, and `test` said `ok ... (not in the Route now)` and `Every check passed.` The Service page of the dashboard shows the check `HTTP GET /health` as `critical` with `HTTP GET http://127.0.0.1:4001/health: 404 File not found`. The page uses this as a trap in Troubleshooting.
  - Dashboard: the form `Add Static Instance` has `Host`, `Port`, `Health check` (TCP or HTTP; with HTTP it shows `HTTP path`), `Interval` (empty means 10 s) and `Timeout` (empty means 2 s). The form was opened and its fields read, and not submitted.
- The time with the default interval (10 s) was not measured here. The page says "up to about 15 seconds", as the user guide does, and the page "Put your process before the container" measured 14 seconds with the default.
- Not tested: containers that Docker runs as replicas (`docker compose up --scale`, which the page "Stop chasing Compose ports" covers), the `--check tcp` flag, and a real frontend.
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
