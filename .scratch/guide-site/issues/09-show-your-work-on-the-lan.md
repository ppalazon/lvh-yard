# 09: Recipe 11: Show your work on the LAN

**What to build:** Write the recipe "Recipe 11: Show your work on the LAN" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Pain

"My teammate cannot open my service."

## Result

A teammate with a Workstation opens it at `<service>.<workstation>.<Base Domain>`.

## Commands to use

- `lvh-yard publish <service> --scope yard`.
- `lvh-yard test <service> --workstation ws-a` as the check.

## Diagram idea

One diagram: Yard Scope across two Workstations.

## Verification

`mise run dev:up`. Repeat the dashboard-has-no-login warning: Scope decides who gets a hostname, not who can connect.

## Open question

A device that cannot run a Workstation, such as a phone, cannot use LVH Yard on its own, because a Workstation needs Docker with Compose. The recipe makes no promise for it. The maintainer wants to think about it later.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
