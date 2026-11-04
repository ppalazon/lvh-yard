# Route Profiles: auth and headers for a Publication, on every Workstation

Status: done

Words follow `GLOSSARY.md` (Route Profile, Publication, Home Workstation, Alias, Out-of-range Instance). Decisions follow ADR-0004 (Route Profiles are written in a Home file and travel through Consul KV) and ADR-0002. Builds on `.scratch/init/spec.md` (the `route-profiles.yaml` template) and `.scratch/status/spec.md`.

## Problem Statement

A user wants to put basic auth in front of a Service, add headers to its requests or answers, set `X-Forwarded-Proto: https`, allow only some addresses, or send it through a forward-auth service. LVH Yard builds Traefik's whole configuration, so there is no place to add Traefik middlewares. And a Service published to the Yard must be protected the same way on every teammate's Workstation, without each one copying Traefik configuration by hand.

## Solution

The user writes Route Profiles as Traefik middlewares, in Traefik's own format, in `route-profiles.yaml` on the Home Workstation. The dashboard reads the file, refuses profiles that could not work on another Workstation, and copies the valid ones into its Consul KV. A Publication names zero or more Route Profiles. Every Workstation that routes it, Aliases included, reads the profiles from the Home Workstation's KV and gives them to Traefik as `<workstation>_<profile>`. Auth profiles always carry a warning: a direct connection to the Instance's address skips Traefik, so the Service must check its users itself.

## User Stories

1. As a user, I want to write Route Profiles in one file, in Traefik's middleware format, so that I can use what Traefik's docs show.
2. As a user, I want profiles for basic auth, forward auth, request headers, response headers, `X-Forwarded-Proto: https`, CORS headers and an IP allowlist, so that I can protect and shape my Services.
3. As a user, I want a profile to be a Traefik `chain` of other middlewares in the same file, so that I can combine them under one name.
4. As a user, I want the dashboard to pick up changes to the file without a restart, so that I can edit and try.
5. As a user, I want `lvh-yard publish web --profile basic-auth --profile cors` to set a Publication's profiles, so that I can use them from the terminal.
6. As a user, I want `lvh-yard publish web` without `--profile` to keep the current profiles, so that publishing again does not drop them.
7. As a user, I want `--no-profiles` to remove all profiles, so that I can open a Service again.
8. As a user, I want to choose profiles on the Service page from the names in my file, so that I do not have to type them.
9. As a user, I want naming a profile that the file does not define refused, with the names that exist, so that I do not save a typo.
10. As a user, I want my profiles applied on my own Workstation's Route for the Service, so that `web.lvh.me` asks for auth.
11. As a teammate, I want a Yard-scoped Service's profiles applied on my Workstation's Route too, so that it is protected the same way here.
12. As a teammate, I want my Alias to a Remote Service to apply that Service's profiles, so that a short hostname does not open it.
13. As a user, I want my profiles never to clash with a teammate's profiles of the same name, so that each Workstation's `basic-auth` means its own users.
14. As a user, I want a profile change on the Home Workstation to reach every Workstation within seconds, so that I never copy files.
15. As a user, I want a profile that uses a file on disk (`basicAuth.usersFile`, `digestAuth.usersFile`) refused, with users written inline as hashes suggested, so that it works on every Workstation.
16. As a user, I want a `forwardAuth` address on loopback or a Docker bridge network refused, so that other Workstations do not send auth to their own computer.
17. As a user, I want Traefik plugins refused in profiles, so that a profile never depends on a plugin a teammate has not installed.
18. As a user, I want a chain that names a middleware outside the file refused, so that every profile travels whole.
19. As a user, I want a broken file (invalid YAML, unknown middleware type) to keep the last good profiles, and show the error, so that a bad save never opens or breaks my Services.
20. As a user, I want the error shown on the status page and in the header's Traefik dot, so that I notice it.
21. As a user, I want a Publication that names a profile that no longer exists to fail closed, so that a missing auth never opens the Service.
22. As a user, I want that Publication to show "unknown Route Profile" on its Service page, in the API and in `lvh-yard list`, so that I know why its hostname stopped working.
23. As a user, I want every profile with basic auth or forward auth to show a warning that a direct connection to the Instance's address and port skips it, so that I remember the Service must check its users itself.
24. As a user, I want that warning on the Service page, in the API's Service warnings and in `lvh-yard publish`, so that I see it wherever I set the profile.
25. As a user, I want to give a Core Service profiles, for example basic auth on `consul`, so that I can protect it.
26. As a user, I want to list my profiles with `lvh-yard profiles`, with their middleware types and any error, so that I can check the file from the terminal.
27. As a user, I want to see a Remote Service's profile names on its page, so that I know why it asks for auth.
28. As a user, I want the file's location to default to the folder `init` wrote, and `YARD_ROUTE_PROFILES` to change it, so that `mise run dashboard` works too.
29. As a user without a file, I want LVH Yard to work as today, with no profiles, so that the file is optional.

## Implementation Decisions

- A new Route Profiles module reads `route-profiles.yaml` (path from `YARD_ROUTE_PROFILES`; the `compose.yaml` template mounts it read-only into the dashboard). The file has Traefik's dynamic configuration shape: `http.middlewares.<name>: { <type>: {...} }`. Each top-level middleware is a Route Profile.
- It watches the file. On a valid read it writes each profile to `lvh-yard/route-profiles/<name>` in this Workstation's Consul KV (the middleware definition as JSON, with any chain members included) and deletes profiles no longer in the file. On an invalid read it writes nothing, and records the error for the status and API.
- Validation refuses `usersFile`, `forwardAuth.address` whose host is in the out-of-range ranges or is `localhost`, plugins, and chains naming middlewares outside the file. A refused profile is an error for that profile; the others still apply. An unknown middleware type (outside an allowlist of Traefik's built-in HTTP middlewares, less rate limits and path rewrites) breaks the whole file instead, as story 19 says: it is most likely a typo in a profile that Services already use, so the last good profiles stay.
- A Publication gains `profiles`, a list of names, stored with it in the Home Workstation's KV. Setting it checks the names against this Workstation's profiles.
- The Route builder, on every Workstation, for each Route of a Publication (its own hostname, a remote hostname, or an Alias): reads the named profiles from the Home Workstation's KV, adds each middleware (and chain members) to the Traefik configuration as `<home-workstation>_<name>`, chain references renamed the same way, and lists them on the Route in order. A missing profile is still listed on the Route, so Traefik refuses the Route: fail closed. A Workstation whose KV cannot be read already loses its Routes.
- All middlewares reach Traefik through the HTTP provider; Traefik needs no file provider.
- Auth warning: a new Service warning for Publications naming a profile whose middleware (or chain member) is `basicAuth`, `digestAuth` or `forwardAuth`. Unknown Route Profile is another Service warning.
- API: `PublicationRequest` gains `profiles` (absent keeps the current ones; empty clears). `GET /api/workstation/route-profiles` lists this Workstation's profiles, types and errors. Service models carry `profiles`. Documented in `openapi.yaml`.
- CLI: `publish --profile` (repeatable) and `--no-profiles`; `profiles` subcommand.
- Status: the Traefik part reports a broken file or refused profiles as a warning.
- Basic-auth hashes in KV are readable by the whole Yard; the Vault study (`.scratch/vault-secrets/`) is about that.

## Testing Decisions

- A good test checks only what a user could see: API answers, the Traefik configuration the dashboard serves, pages, CLI output.
- Seam 1, the dashboard, with real Consul from Testcontainers and the two-datacenter Yard. Each test writes a `route-profiles.yaml` to a temporary path given by `YARD_ROUTE_PROFILES`. Tests check: profiles listed; a Publication naming them; middlewares and Route middleware lists in the Traefik configuration of the Home Workstation and of the remote one (`<workstation>_<profile>`); Aliases applying them; chains renamed; each refusal; a broken file keeping the last good profiles; a removed profile failing closed with the warning; the auth warning; a change in the file reaching the remote configuration.
- One end-to-end test with a real Traefik: a basic-auth profile answers 401 without credentials and passes with them.
- Seam 2, the CLI, with the stub dashboard: `--profile`, `--no-profiles`, `profiles` output, warnings printed.
- The OpenAPI test covers the new fields and endpoint.
- Prior art: the Traefik configuration test, the remote services test, the aliases test, and the reachability test (real Traefik).

## Out of Scope

- Real HTTPS (TLS entrypoint, certificates); a separate feature later.
- Rate limits and path rewrites.
- Writing the file from the dashboard or the CLI.
- Keeping secrets outside Consul KV (see the Vault study).
