# One Consul datacenter per Workstation, linked by WAN federation

Every Workstation runs its own Consul server as a separate datacenter, and the datacenters join through WAN federation. Workstations are equal and come and go, so none of them may depend on another being on.

## Considered Options

- **One cluster with a server on every Workstation**: rejected. Raft needs a majority, and with two servers that is both, so one Workstation turning off stops all changes on the other.
- **One server on one Workstation, agents on the rest**: rejected. Every other Workstation stops working while the server's Workstation is off.

## Consequences

Traefik's Consul Catalog provider reads only one datacenter, so it cannot merge the Yard on its own. See [ADR-0002](0002-traefik-reads-routes-from-lvh-yard.md).
