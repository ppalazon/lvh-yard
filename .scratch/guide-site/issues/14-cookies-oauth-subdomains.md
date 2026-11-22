# 14: Recipe 16: Cookies, OAuth callbacks and subdomains

**What to build:** Write the recipe "Recipe 16: Cookies, OAuth callbacks and subdomains" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Pain

"`localhost:3000` breaks my cookies and my OAuth callback. The identity provider wants a real hostname."

## Result

The app runs at `app.<Base Domain>`, and the cookies and the callback URL use that hostname.

## Commands to use

- `lvh-yard publish app`, then the callback URL `http://app.<Base Domain>/callback`.
- A cookie with `Domain=<Base Domain>` across two Services, as a short example.
- HTTPS for a callback that needs it: a pointer to recipe 3 and recipe 19.

## Diagram idea

A flow diagram of the OAuth redirect with the hostname at each step.

## Verification

Test with a small local app that sets a cookie. Test the tenant-subdomain question of the spec (`acme.app.<Base Domain>`). If a Route cannot answer for it, drop it from the page and say so. Do not promise what a Route cannot do.

## Notes

Warn that with a shared domain, a cookie goes to the owner's address if the DNS changes. Link to the Base Domain page.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
