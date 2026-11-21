# 13: Recipe 8: Compose ports that change on every `compose up`

**What to build:** Write the recipe "Recipe 8: Compose ports that change on every `compose up`" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

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

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
