# 02: Route Profiles change through the API, and the file goes away

**What to build:** You add, replace and remove Route Profiles through the dashboard API, and the dashboard no longer reads `route-profiles.yaml`. A profile added with the API reaches the Routes of every Workstation, Aliases included. See `.scratch/route-profiles-in-kv/spec.md`, user stories 9 to 23, 40, 41, 43 to 45.

**Blocked by:** 01

**Status:** done

- [ ] `GET /api/workstation/route-profiles` answers a list. Each item has the name, the Traefik type and the Services whose Publications name it. A profile with no single type has no type field.
- [ ] `GET` on one name answers the item and its definition in Traefik's format.
- [ ] `PUT` on one name adds or replaces it. `PUT` on the collection adds or replaces a set, all or nothing, and keeps the profiles that it does not name.
- [ ] `DELETE` on one name removes it. If a Publication or a chain names it, the answer is a conflict that names each Service and chain.
- [ ] A refused profile answers a client error problem with the reason, and Consul KV does not change.
- [ ] Profiles keep the same Consul KV key and stored form, so Traefik still gets `<workstation>_<profile>` on every Workstation.
- [ ] The file reader, its polling, the last good copy, the stored errors and `YARD_ROUTE_PROFILES` go away.
- [ ] `lvh-yard init` and `reinit` no longer write `route-profiles.yaml`. `compose.yaml` no longer sets `YARD_ROUTE_PROFILES`, and its mount comment no longer names the file.
- [ ] `lvh-yard profiles` reads the new list and prints each name, type and the Services that use it.
- [ ] The help of `lvh-yard publish --profile` names `lvh-yard profiles` instead of the file.
- [ ] The dashboard tests add profiles through the API. The file reader tests become tests of refused saves.
- [ ] The real-Traefik basic-auth test adds its profile through the API and still answers 401 without credentials.
- [ ] The OpenAPI test covers the new endpoints and the list answer.
- [ ] `docs/user-guide.md` and `docs/development.md` no longer describe `route-profiles.yaml`.
