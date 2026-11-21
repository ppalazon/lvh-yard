# 20: Recipe 19: A real HTTPS certificate on your own domain

**What to build:** Write the recipe "Recipe 19: A real HTTPS certificate on your own domain" with the recipe template of the spec.

**Blocked by:** 01, 06, 19

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Pain

"I own a domain, and I want HTTPS that every browser trusts with no CA to install."

## Result

Traefik serves a public certificate for `*.<own domain>`, and every browser trusts it.

## Commands to use

- Get a wildcard certificate for the Base Domain through DNS-01. Name the method, and say that the page does not teach one tool.
- `lvh-yard ws tls https import --ca ... --cert ... --key ...`, then `enable`.
- A second wildcard for each remote Workstation (`*.ws-b.<Base Domain>`), as the user guide says.

## Diagram idea

A diagram of the certificate chain: a public CA signs the wildcard, Traefik shows it, the browser trusts it.

## Verification

Test only the `import` step, with a certificate from a test CA. Label the issuance step "not tested by the maintainer" until someone runs it with a real domain. Do not invent steps for a DNS provider.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
