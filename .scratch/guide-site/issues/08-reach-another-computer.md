# 08: Recipe 1: Reach another computer

**What to build:** Write the recipe "Recipe 1: Reach another computer" with the recipe template of the spec.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Pain

"A service runs on another computer in my LAN, and I want to open it by name."

## Result

The reader opens the service from this computer at a hostname, with the right variant for what runs on the other box.

## Commands to use

- Variant A, a second Workstation joins the Yard: `lvh-yard ws yard share`, `lvh-yard ws yard join`, `lvh-yard publish <service> --scope yard`, `lvh-yard catalog`. Hostname: `<service>.<workstation>.<Base Domain>`.
- Variant B, a Workstation with Traefik off: `lvh-yard ws set traefik off` and the commands that `ws set` prints. Read the user guide section "A host without Traefik".
- Variant C, a box with no LVH Yard (a NAS, a Raspberry Pi): `lvh-yard register static <service> --host 192.168.1.50 --port 9000`, then `publish`.
- `lvh-yard test <service>` and `lvh-yard test <service> --workstation ws-b` as the check.

## Diagram idea

A decision diagram first: "What runs on the other box?" with three outcomes. Then one small flow diagram for each variant.

## Verification

Variants A and B: `mise run dev:up` starts two test Workstations on this computer. Variant C: use a listener on another address of this computer. Run it on a real LAN box if the maintainer has one, and label the page "tested by hand" for that part.

## Notes

The Yard needs the Gossip Key and the Invitation password to travel by different ways. Say so in the steps. Read the user guide section "Joining a Yard" before you write.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
