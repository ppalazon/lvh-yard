# 19: Recipe 3: Test HTTPS with a Workstation CA

**What to build:** Write the recipe "Recipe 3: Test HTTPS with a Workstation CA" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Pain

"I need HTTPS in development, and I do not want to click through a browser warning."

## Result

Traefik serves HTTPS with a certificate that your Workstation CA signs, and your browser trusts that CA.

## Commands to use

- `lvh-yard ws tls https create`, `lvh-yard ws tls https enable`, and the commands that they print.
- Browser trust for Linux, macOS and Windows, as tabs, and for Firefox. Read the user guide section "Browser trust".
- `lvh-yard ws tls ca issue service` for a Service Certificate, and `lvh-yard publish <service> --mutual-tls on` for mutual TLS to a Service.
- `curl --cacert` as the check.

## Diagram idea

A diagram of the three TLS Parts of Traefik (HTTPS, Client Certificate authentication, mutual TLS to Services), and a sequence diagram of the browser trusting the Workstation CA.

## Verification

`mise run dev:up` for the commands and `curl`. Browser trust is by hand: label each OS. Read ADR-0016 and ADR-0018. Warn that the Workstation CA key stays on the Workstation, and that a trusted CA can sign for any name.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
