---
status: accepted
---

# Traefik reads its routes from LVH Yard, not from Consul tags

Each Workstation's Traefik reads its dynamic configuration from the LVH Yard on the same Workstation, through Traefik's HTTP provider. LVH Yard builds it from the Instances and Health in every datacenter, the Publications in each Home Workstation's Consul KV, and the Aliases in the local Consul KV. A Publication belongs to a Service, while Consul tags belong to each Instance. With tags, new Instances would start unpublished and Instances could disagree. This also keeps hostname conflicts in one place, and lets one Traefik see the whole Yard (see [ADR-0001](0001-one-consul-datacenter-per-workstation.md)).

## Considered Options

- **`traefik.*` tags on each Instance, read by the Consul Catalog provider**: the brief's original plan. Rejected for the reasons above.
- **Publications in KV, with LVH Yard rewriting Instance tags to match**: rejected. It fights any other tool that owns those registrations.

## Consequences

- LVH Yard runs on every Workstation. Consul is its only store, so it needs no database of its own.
- Traefik gets new routes only while LVH Yard runs. When a fetch fails, Traefik keeps its last configuration and retries (checked in `pkg/provider/http/http.go`).
