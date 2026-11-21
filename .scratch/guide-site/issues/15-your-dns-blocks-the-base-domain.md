# 15: Recipe 18: Your DNS blocks the Base Domain

**What to build:** Write the recipe "Recipe 18: Your DNS blocks the Base Domain" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"`app.lvh.me` does not resolve on my network, or it stops resolving offline."

## Result

You find the cause and switch to an own wildcard domain, a wildcard in your resolver, or `localhost`.

## Commands to use

- `getent hosts app.<Base Domain>` or `dig`, and the error that tells a DNS block.
- `lvh-yard ws set base-domain <domain>` and `lvh-yard ws compose recreate`.
- A resolver rule such as `address=/lab.home/127.0.0.1` in dnsmasq.

## Diagram idea

A decision diagram: the name does not resolve, so does your router block loopback answers? Offline? Then the choice.

## Verification

Reproduce with a local dnsmasq on a test port if you can, or label the router part "not reproduced". Test the change on a test Workstation.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
