# 01: One place for the Route Profile checks

**What to build:** The checks that the file reader makes on each Route Profile move into one unit that every later write path calls. Behavior does not change. This is the prefactor for the rest of `.scratch/route-profiles-in-kv/spec.md`. See its Implementation Decisions.

**Blocked by:** None (can start immediately)

**Status:** done

- [ ] One unit checks a set of profiles and answers each problem with the profile name and the reason.
- [ ] It refuses `basicAuth.usersFile`, plugins and `forwardAuth` to an Out-of-range address. It refuses an unknown Traefik type and a name that Traefik does not accept. It refuses a chain that names a profile outside the set and outside Consul KV.
- [ ] A chain can name a profile in the same set or one already in Consul KV.
- [ ] The file reader calls this unit, and the existing Route Profile tests stay green with no change.
- [ ] Tests for the unit go through the dashboard seam, as the existing tests do.
