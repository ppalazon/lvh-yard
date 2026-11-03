# 02: An Alias with the same name is the last fallback

**What to build:** With an Alias `web` to a teammate's `web`, `web.lvh.me` reaches the teammate's Service when none of the user's own `web` Instances is healthy, instead of the Alias being hidden.

**Blocked by:** 01

**Status:** done

- [x] On the Home Workstation's own short hostname, the `alias` kind uses the active Alias's Remote Service's passing, in-range Instances
- [x] An Alias with no same-named Service works as before
- [x] Other Workstations never use this Workstation's Aliases
- [x] The Aliases page and `lvh-yard alias list` show an Alias acting as a fallback
- [x] Tested with the two-datacenter Yard
