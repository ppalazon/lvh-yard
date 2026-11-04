# 02: The first start sets up yard, consul-ui and traefik-admin once, as ordinary Services

**What to build:** When the dashboard starts and the setup marker does not exist in Consul KV, the dashboard registers and publishes `yard`, `consul-ui` and `traefik-admin`. Then it writes the marker and never does the setup again. After that, the three Services are ordinary: the user can remove, unpublish or change them. The Core Service concept goes away from the dashboard, and the name `consul` is refused. See `.scratch/own-services/spec.md`, user stories 1 to 21 and 28 to 39, and ADR 0006.

**Blocked by:** 01

**Status:** done

- [x] On a Consul with no marker, the setup writes a Static Instance with an HTTP check and a Publication with Local Scope for each of the three Services:
  - `yard` at the dashboard's address and port, with a check on `/actuator/health`. When the dashboard binds to `0.0.0.0`, the address is loopback.
  - `consul-ui` on loopback at Consul's HTTP port, with a check on `/v1/status/leader`.
  - `traefik-admin` on loopback at Traefik's admin port, with a check on `/ping`.
- [x] The setup uses ordinary Static Instance IDs and no special mark.
- [x] If a Service with one of the names has an Instance, a Container Binding or a Publication, the setup leaves it alone and writes the others.
- [x] After the setup, the marker exists. When the marker exists, a restart writes nothing, and a removed Service stays removed.
- [x] The setup runs in the background. While Consul does not answer, it tries again every 5 seconds. When it starts to fail, it logs one line. When it finishes, it logs one line.
- [x] The old keeper of the Core Services and its key go away.
- [x] Every refusal that protected a Core Service goes away, with its 409 answer: removing, unpublishing, Yard Scope, Instance Priority and Route Profile changes.
- [x] The names `yard` and `traefik` are free for any Service and Alias.
- [x] The name `consul` is refused for a Service, an Alias and an Import. The answer is 400 with the reason "consul is Consul's own entry in its catalog". The Aliases page and the Containers page show the reason.
- [x] Consul's own server entry stays hidden from the Catalog.
- [x] The `core` field goes away from the Service and Instance JSON, and the Core Service mark goes away from Consul's service metadata.
- [x] A teammate's three Services show as ordinary Remote Services.
- [x] The Traefik configuration has no fallback Route for the dashboard. When `yard` is unpublished, no Route exists for `yard.<base domain>`.
- [x] The Service page shows the same controls for every Service.
- [x] The API documentation says nothing about Core Services.
- [x] The test support waits for the marker key. The Core Services test becomes the test of the setup, with the "only once" tests on the remote Workstation.
