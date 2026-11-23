# 21: Recipe 12: Protect a dev service with Route Profiles

**What to build:** Write the recipe "Recipe 12: Protect a dev service with Route Profiles" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## Pain

"I want a password, an IP allow list, or CORS headers on a service, and I do not want to touch its code."

## Result

A Route Profile adds a Traefik middleware to the Route of a Service.

## Commands to use

- `lvh-yard profiles set profiles.yaml`, `lvh-yard publish admin --profile basic-auth`, `lvh-yard profiles`, `lvh-yard profiles show`, `lvh-yard profiles edit`.
- A profile for basic auth, one for an `ipAllowList`, and one for CORS headers, in Traefik's file format.
- The dashboard as a content tab for the Route Profiles page.

## Diagram idea

A flow diagram: request, middlewares in order, Instance.

## Verification

`mise run dev:up` and `curl -i`. Read the user guide section "Route Profiles" for what the dashboard refuses.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/recipes/https/route-profiles.md`, titled "Protect a dev service with Route Profiles" as in the outline. It is in `draft_docs`, and the navigation lists it in "HTTPS and access". The stage 1 text is in `drafts/21-route-profiles.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. The diagram is the order of the middlewares: Route, `office-only` (ipAllowList), `basic-auth` (basicAuth), Instance. The warning that a profile protects the hostname and not the port comes before `publish`.
- Tested on a throwaway Workstation in `/tmp` (folder `rcrpw`, Workstation name `ws-a`, other ports than the defaults) with two Static Instances (`python3 -m http.server` on 4000 and 4001). The outputs on the page are real, and the Traefik range of the test is the one on the page.
  - `htpasswd -nbB` printed a bcrypt hash. `profiles set` printed `Saved the Route Profiles office-only, basic-auth, cors.`, `profiles` listed them as `not used`, `publish web --profile office-only --profile basic-auth` printed the `risk: [Authenticated Route]` line, and `profiles` then said `used by web` and `used by api`.
  - Allow list: with `192.168.1.0/24` alone, the hostname answered `403 Forbidden` from the same computer. With `127.0.0.1/32` added, it answered `401 Unauthorized`, and `200 OK` with the password (the page does not show the password request, because the hook gitleaks refuses a `curl` line with a user and a password). `profiles edit office-only` worked with a non-interactive `EDITOR` (a `sed` command), and the Routes used the new range after about 10 seconds. An interactive editor was not tested.
  - CORS: `api` answered `Access-Control-Allow-Origin: http://localhost:5173` for that origin, the preflight (`OPTIONS` with `Access-Control-Request-Method`) answered `200` with the allowed methods and headers, and another origin got no CORS header.
  - Refusals: `publish web --profile team-login` printed `No Route Profile is named team-login. The profiles are: basic-auth, cors, office-only.` with exit 1, and `profiles remove cors` printed `cors is in use, so it cannot be removed. The Publications of these Services name it: api.` with exit 1. `publish --no-profiles` and `profiles remove office-only` worked. An unknown profile in a Publication cannot be made with the CLI (it refuses the name), so the page leaves out the "unknown Route Profile" error.
  - Dashboard: `Route Profiles` has `New Route Profile`, and a row has `Change` and `Remove`. The form has `Name`, `Route options`, `Save Route Profile` and an `Examples` sidebar (Client Certificate, Basic auth, IP allow list, Request headers, CORS, Forward auth, Chain and more). I created `lan-only` with YAML in the form. On the Service page, the card `Route Profiles` has the selector `Route Profile to add` and `Add`. I added `lan-only` to `web`, and `profiles` said `used by web`. The icons that change the order and the red cross were not clicked.
- Not tested: the chain profile, `forwardAuth`, the other Examples, Yard Scope with profiles, and the Windows form of the commands (`htpasswd` is not on Windows by default).
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
