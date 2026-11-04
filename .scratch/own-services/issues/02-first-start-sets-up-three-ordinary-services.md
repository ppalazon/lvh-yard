# 02: The first start sets up yard, consul-ui and traefik-admin once, as ordinary Services

**What to build:** When the dashboard starts and the setup marker does not exist in Consul KV, the dashboard registers and publishes `yard`, `consul-ui` and `traefik-admin`. Then it writes the marker and never does the setup again. After that, the three Services are ordinary: the user can remove, unpublish or change them. The Core Service concept goes away from the dashboard, and the name `consul` is refused. See `.scratch/own-services/spec.md`, user stories 1 to 21 and 28 to 39, and ADR 0006.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] On a Consul with no marker, the setup writes a Static Instance with an HTTP check and a Publication with Local Scope for each of the three Services:
  - `yard` at the dashboard's address and port, with a check on `/actuator/health`. When the dashboard binds to `0.0.0.0`, the address is loopback.
  - `consul-ui` on loopback at Consul's HTTP port, with a check on `/v1/status/leader`.
  - `traefik-admin` on loopback at Traefik's admin port, with a check on `/ping`.
- [ ] The setup uses ordinary Static Instance IDs and no special mark.
- [ ] If a Service with one of the names has an Instance, a Container Binding or a Publication, the setup leaves it alone and writes the others.
- [ ] After the setup, the marker exists. When the marker exists, a restart writes nothing, and a removed Service stays removed.
- [ ] The setup runs in the background. While Consul does not answer, it tries again every 5 seconds. When it starts to fail, it logs one line. When it finishes, it logs one line.
- [ ] The old keeper of the Core Services and its key go away.
- [ ] Every refusal that protected a Core Service goes away, with its 409 answer: removing, unpublishing, Yard Scope, Instance Priority and Route Profile changes.
- [ ] The names `yard` and `traefik` are free for any Service and Alias.
- [ ] The name `consul` is refused for a Service, an Alias and an Import. The answer is 400 with the reason "consul is Consul's own entry in its catalog". The Aliases page and the Containers page show the reason.
- [ ] Consul's own server entry stays hidden from the Catalog.
- [ ] The `core` field goes away from the Service and Instance JSON, and the Core Service mark goes away from Consul's service metadata.
- [ ] A teammate's three Services show as ordinary Remote Services.
- [ ] The Traefik configuration has no fallback Route for the dashboard. When `yard` is unpublished, no Route exists for `yard.<base domain>`.
- [ ] The Service page shows the same controls for every Service.
- [ ] The API documentation says nothing about Core Services.
- [ ] The test support waits for the marker key. The Core Services test becomes the test of the setup, with the "only once" tests on the remote Workstation.
