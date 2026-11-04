# 02: Apply Route Profiles on the Home Workstation's Routes

**What to build:** `lvh-yard publish admin --profile basic-auth` makes `admin.lvh.me` ask for a password on the Home Workstation, with a warning that a direct connection skips it.

**Blocked by:** 01, core-services/03

**Status:** ready-for-agent

- [x] `PublicationRequest` takes `profiles`; absent keeps them, empty clears them; unknown names are refused with the names that exist
- [x] The Traefik configuration has the middlewares as `<workstation>_<profile>` and lists them on the Route in order
- [x] An auth warning (basicAuth, digestAuth, forwardAuth, also inside a chain) is a Service warning on the page, in the API and in `lvh-yard publish`
- [x] Core Services accept profiles
- [x] The Service page picks profiles from the file's names
- [x] `lvh-yard publish --profile` (repeatable) and `--no-profiles`
- [x] An end-to-end test with real Traefik: 401 without credentials, 200 with them
