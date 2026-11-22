# 04: Start: What LVH Yard solves

**What to build:** The first page of the guide. It names the six pains, shows the idea in one diagram, and lists what LVH Yard does not do.

**Blocked by:** 01

**Status:** ready-for-human

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

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
