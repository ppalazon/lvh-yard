# 10: Route Profiles

Status: done

Blocked by: 03

Spec: `../spec.md`.

## What to build

A user manages the Route Profiles of this Workstation in Traefik's file format.

- `profiles`: lists the Route Profiles with their types and the Services that use them.
- `profiles show <name>`: prints one in Traefik's file format.
- `profiles set <file>`: adds or replaces each profile of the file. `-` reads standard input. A refused set shows one line for each wrong profile.
- `profiles edit <name>`: opens the profile in `$EDITOR`, which can hold arguments such as `code --wait`, and sends the change.
- `profiles remove <name>`.
- The conversion between Traefik's YAML (`http.middlewares.<name>`) and the API's JSON, with the YAML module. See `Profiles.java` and `RouteProfileFile.java`. This is the only code that parses YAML besides the openapi test.
- The client methods and schemas that these need.

Port the cases of `ProfilesTest` that test CLI behavior. Keep the round trip: what `profiles show` prints, `profiles set` takes back unchanged, including a string such as `"100"`, `"true"` or `"null"`.

## Acceptance criteria

- [x] `profiles show NAME | profiles set -` leaves the profile as it was.
- [x] A string that looks like a number stays a string through the round trip.
- [x] `profiles edit` works with an editor given as `code --wait`.
- [x] On a real test Yard, a Publication that uses a set profile gets it in its Route.
- [ ] `mise run check` passes, and the openapi test knows the new calls.

## Comments

- 2026-11-10: The integration owner reported that `go vet`, all Go tests and the build passed.
- On the live two-Workstation Yard, strings `"100"`, `"true"` and `"null"` stayed strings through the show/set round trip.
- With `EDITOR="python /tmp/lvh-yard-go-live/editor.py --wait"`, a real editor invocation changed `"100"` to `"101"`. The live Traefik Route returned the header value `101`.
- `mise run check` and hosted checks remain unverified. No tests, builds, linters or formatters ran during integration.
