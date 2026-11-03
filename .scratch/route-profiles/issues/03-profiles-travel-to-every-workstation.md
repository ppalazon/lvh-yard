# 03: Route Profiles travel to every Workstation and Alias

**What to build:** A teammate reaching the user's Yard-scoped `admin`, or an Alias to it, is asked for the same password, with no file copied.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] Every Workstation routing a Publication reads its profiles from the Home Workstation's KV
- [ ] Middlewares are named `<home-workstation>-<profile>`; chain members and references are renamed the same way
- [ ] Aliases apply the Remote Service's profiles
- [ ] A Remote Service's page shows its profile names
- [ ] A change in the Home file reaches the remote Traefik configuration within seconds
- [ ] Tested with the two-datacenter Yard
