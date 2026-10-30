# 07: Aliases

**What to build:** A developer gives a Remote Service a short Alias on their own Workstation, such as `grafana.lvh.me` for `grafana.ws-b.lvh.me`, from the Aliases page or with `yard alias`. An Alias only exists on that Workstation. A Service of that Workstation with the same name wins, and the Alias is flagged as hidden.

**Blocked by:** 06 (Share a Service with Yard Scope)

**Status:** done

- [x] An Alias names a Remote Service by Workstation and Service name, and is stored in this Workstation's Consul KV.
- [x] An Alias name follows the Service name rules, and `yard` is refused.
- [x] This Workstation's Traefik configuration has a Route for `<alias>.<base-domain>` to the Remote Service's Instances.
- [x] No other Workstation gets a Route for the Alias.
- [x] When this Workstation has a published Service with the Alias's name, the Service's Route wins and the Alias shows as hidden.
- [x] An Alias to a Remote Service that is no longer Yard-scoped, or whose Workstation is unreachable, has no Route and shows why.
- [x] The Aliases page and `yard alias` add, list and remove Aliases.
