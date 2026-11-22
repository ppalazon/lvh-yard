# 09: Recipe 11: Show your work on the LAN

**What to build:** Write the recipe "Recipe 11: Show your work on the LAN" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Pain

"My teammate or my phone cannot open my service."

## Result

A teammate with a Workstation opens it at `<service>.<workstation>.<Base Domain>`. A device with no Workstation reaches it through a Traefik that listens on the LAN.

## Commands to use

- `lvh-yard publish <service> --scope yard`.
- `lvh-yard ws set traefik-bind-address <LAN address>` for a device with no Workstation, and the DNS that it needs (an own domain or a resolver wildcard that points to the LAN address, not to `127.0.0.1`).
- `lvh-yard test <service> --workstation ws-a` as the check.

## Diagram idea

Two diagrams: Yard Scope across two Workstations, and a phone reaching Traefik on the LAN.

## Verification

`mise run dev:up` for Yard Scope. The phone case needs a real device. Label it "tested by hand", or run it with `curl --resolve` from the second test Workstation. Repeat the dashboard-has-no-login warning: Scope decides who gets a hostname, not who can connect.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
