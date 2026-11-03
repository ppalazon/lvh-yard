# Instance Priority is chosen by Instance kind, from Consul Health

A Route prefers Instances by kind: by default Static, then Bound, then the Remote Service of an Alias with the same name. It uses only the first kind with a passing Health. The dashboard makes this choice from Consul Health each time it builds Traefik's configuration. This lets one hostname go to a copy started in a terminal while it runs, and back to the Docker one when it stops. The order is stored with the Publication in the Home Workstation's Consul KV, so every Workstation uses it. Each Workstation drops its Out-of-range Instances before it picks a kind.

## Considered Options

- **Weights for each Instance**: rejected. Weights spread traffic; they never stop sending it to the Docker copy.
- **Traefik's `failover` service**: rejected. It switches only on Traefik's own HTTP health checks, which would repeat Consul's checks and cannot express a TCP check.
- **An order for each Instance, not each kind**: rejected. Bound Instances come and go with their containers, so an order for each Instance would not last.

## Consequences

- An Alias with the same name as a Service of this Workstation is no longer hidden: it becomes the last fallback.
- The Publication's KV value holds the default order as the plain Scope text, and any other order as JSON. So older Workstations still read a Publication with the default order, but cannot read one with its own order.
- A Service can turn the Instance Priority off with an empty order. Then its passing Static and Bound Instances share the Route, and a same-named Alias is never used. Off is stored as JSON, so older Workstations cannot read it.
- Falling back depends on checks. Every Instance has at least a TCP check, so a stopped one turns critical.
