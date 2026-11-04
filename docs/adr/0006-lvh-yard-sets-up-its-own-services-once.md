# LVH Yard sets up its own Services once, and then they are ordinary Services

On its first start against a Consul with no setup marker, the dashboard registers three Services with Local Scope: `yard`, `consul-ui` and `traefik-admin`. It writes the marker in Consul KV and never sets them up again. After that, you can remove, unpublish or change them as any other Service. Status reads Consul and Traefik at the addresses from the environment variables, so it does not depend on these Services.

## Considered Options

- **Core Services that the dashboard keeps on every start**: rejected. You cannot remove or change such Services, they reserve three names, and their Status Part only repeats the Consul and Traefik checks. Routes to them are a convenience, not a need, because the dashboard is always at its own address.
- **Setup commands that `lvh-yard init` prints for you to run**: rejected. A new Workstation then has no hostnames until you do extra steps.

## Consequences

- If you remove one of the three Services, it stays removed. Register it again to get it back.
- The name `consul` is refused for a Service and an Alias, because Consul's catalog already holds its own server entry under that name. That is why the setup uses `consul-ui`.
- If a Service with one of the three names exists at the first start, the setup leaves it alone.
- Traefik gets no Route for `yard.<base domain>` unless the `yard` Service is published.
