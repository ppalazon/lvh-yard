# 23: Recipe 20: Open the dashboard to your LAN with a password

**What to build:** Write the recipe "Recipe 20: Open the dashboard to your LAN with a password" with the recipe template of the spec.

**Blocked by:** 01, 19, 21

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"I want to open the dashboard from another computer, but it has no login."

## Result

`yard.<own domain>` asks for a password. The dashboard itself still listens on `127.0.0.1`.

## Commands to use

- `lvh-yard profiles set` with a basic-auth profile, `lvh-yard publish yard --profile basic-auth`.
- `lvh-yard ws set traefik-bind-address <LAN address>`, and keep `dashboard-bind-address` at `127.0.0.1`.
- HTTPS: a pointer to recipes 3 and 19. A reader needs an own domain that points to the Workstation.
- `curl -H 'Host: yard.<Base Domain>' http://<LAN address>/` as the check: `401` without the password, `200` with it.

## Diagram idea

A diagram: a remote browser, Traefik on the LAN address (password), the dashboard on loopback. Show the bypass when the dashboard listens on `0.0.0.0`.

## Verification

`mise run dev:up`. Test the three conditions of the spec. State what happens over plain HTTP. A public server is not tested: use the approved text and link to the Advanced page.

## Notes

Warn first: anything that runs on the Workstation still reaches the dashboard without a password.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
