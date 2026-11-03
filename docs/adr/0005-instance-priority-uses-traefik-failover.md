# Instance Priority uses Traefik's failover, by Instance kind

A Route prefers Instances by kind: by default Static, then Bound, then the Remote Service of an Alias with the same name. It uses only the first kind with a passing Health, through Traefik's `failover` service. This lets one hostname go to a copy started in a terminal while it runs, and back to the Docker one when it stops. The order is stored with the Publication in the Home Workstation's Consul KV, so every Workstation uses it. Each Workstation drops its Out-of-range Instances before it picks a kind.

## Considered Options

- **Weights for each Instance**: rejected. Weights spread traffic; they never stop sending it to the Docker copy.
- **An order for each Instance, not each kind**: rejected. Bound Instances come and go with their containers, so an order for each Instance would not last.

## Consequences

- An Alias with the same name as a Service of this Workstation is no longer hidden: it becomes the last fallback.
- Falling back depends on checks. Every Instance has at least a TCP check, so a stopped one turns critical.
