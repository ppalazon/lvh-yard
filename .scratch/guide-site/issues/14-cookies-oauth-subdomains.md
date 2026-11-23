# 14: Recipe 16: Cookies, OAuth callbacks and subdomains

**What to build:** Write the recipe "Recipe 16: Cookies, OAuth callbacks and subdomains" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** done

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

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/name/cookies-and-oauth.md`, in "Name your services" in the nav.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup: a throwaway Workstation in `/tmp` (folder `ws` in `/tmp/rc14`, other ports than the defaults, Base Domain `lvh.me`) and two copies of the script of the page (`app` on 4000 and `api` on 4001), both on `127.0.0.1`. The page uses port 80, so its commands have no port, and the test used the HTTP port of the throwaway Workstation. The outputs on the page come from the script as the page shows it, which prints the `Host` header without the port. The Workstation, the apps and the files are removed.
- The pain, reproduced with `curl` and a cookie jar: a cookie that `localhost:4000/login` set came back to `localhost:4001`. A cookie ignores the port (RFC 6265).
- Through the Workstation: the app received `Host: app.lvh.me`, so Traefik passes the `Host` header unchanged. After `/login` at `app.lvh.me`, `app.lvh.me` got `session=app; sso=1` and `api.lvh.me` got `sso=1`. The `curl` jar stored `sso` for `.lvh.me` and `session` for `app.lvh.me` only. `/callback?code=abc` at `app.lvh.me` reached the app with both cookies.
- Tenant subdomain: `http://acme.app.lvh.me` answered `404 page not found`, so a Route answers for `<service>.<Base Domain>` only, as the spec guessed. The page drops tenant subdomains and says so under `404 page not found`. A Service `acme` with a Static Instance on port 4000 answered at `acme.lvh.me` (`acme: Host=acme.lvh.me`).
- Not tested: a real identity provider and a browser. The test used `curl` and a fake callback path. The page does not say what any provider accepts. The sentence about HTTPS links to "Test HTTPS with your Workstation CA" since that page is published.
- The warning about a cookie for a shared domain comes from the approved warning of the spec ("A shared domain can change or disappear at any time").
