# 22: Recipe 13: Require a Client Certificate for an internal tool

**What to build:** Write the recipe "Recipe 13: Require a Client Certificate for an internal tool" with the recipe template of the spec.

**Blocked by:** 01, 19

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"Only people with my certificate should open this tool."

## Result

A Route Profile with `clientCertificate: {}` makes the Route answer only on HTTPS, and only with a Client Certificate.

## Commands to use

- The commands of the user guide section "Client Certificates": `ws tls client ...` and `ws tls ca issue ...`. Confirm each name with `--help`.
- `lvh-yard profiles set` with `clientCertificate: {}`, then `publish --profile`.
- `curl --cert` as the check, and browser import for each OS.

## Diagram idea

A sequence diagram of the browser showing its certificate to Traefik.

## Verification

`mise run dev:up` and `curl`. Browser import is by hand.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
