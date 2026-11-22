# 11: Recipe 2: Name every HomeLab service

**What to build:** Write the recipe "Recipe 2: Name every HomeLab service" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

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

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
