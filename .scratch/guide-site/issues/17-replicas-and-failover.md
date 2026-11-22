# 17: Recipe 9: Replicas, health checks and failover

**What to build:** Write the recipe "Recipe 9: Replicas, health checks and failover" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

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

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
