---
status: accepted
---

# Consul, Traefik and the dashboard keep host networking

All three services keep `network_mode: host`. Traefik must reach Instances where they are: a process on the Workstation's loopback, a container on any Docker network, or an address on the LAN. The dashboard follows containers through the Docker socket and gives Traefik their addresses. To limit what the Workstation exposes, the bind addresses are enough: Traefik and the dashboard listen on `127.0.0.1` by default, the HTTP API of Consul listens on loopback, and only the Consul federation ports face the LAN.

## Considered Options

- **A compose network, with service names and published ports for the dashboard, 80 and 443**: rejected. In that network, `127.0.0.1` is the container itself, so a Static Instance on loopback gets no Route. Traefik also cannot reach a container on another Docker network, unless it joins every one of them. The ports 8300 and 8302 would need publishing too, with `-advertise-wan` on Consul, so the Workstation would open more than three ports.
- **Only Consul in a compose network**: rejected. The dashboard and Traefik, on the host, would then need a published port to reach the HTTP API of Consul.

## Consequences

- Consul has no DNS for Traefik. `dns:` is not allowed with host networking, and a resolver address has no port, while the DNS of Consul listens on port 8600. Traefik needs no Consul DNS, because its Routes carry the addresses that the dashboard read from the HTTP API of Consul.
- The three Services that the first setup registers keep their addresses on the Workstation, and not container IPs or Compose service names (see [ADR-0006](0006-lvh-yard-sets-up-its-own-services-once.md)).
- To use a compose network later, change this ADR first: it also changes what an Instance on loopback means, and the Out-of-range rule.
