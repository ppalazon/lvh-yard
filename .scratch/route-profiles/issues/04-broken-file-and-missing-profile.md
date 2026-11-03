# 04: A broken profiles file or a missing profile

**What to build:** A bad save never opens or breaks Services: a broken file keeps the last good profiles, and a Publication naming a profile that is gone fails closed and says why.

**Blocked by:** 02, status/02

**Status:** ready-for-agent

- [ ] Invalid YAML or an unknown type keeps the last good profiles in KV and records the error
- [ ] The error shows in `GET .../route-profiles`, `lvh-yard profiles`, and as a warning in the status's Traefik part and header dot
- [ ] A Publication naming a missing profile keeps it on the Route, so Traefik refuses the Route
- [ ] "Unknown Route Profile" is a Service warning on the page, in the API and in `lvh-yard list`
- [ ] Tested on the Home and the remote Workstation
