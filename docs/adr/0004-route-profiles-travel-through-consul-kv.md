# Route Profiles are written in a Home file and travel through Consul KV

A Route Profile is a Traefik middleware, written in Traefik's own format in `route-profiles.yaml` on its Home Workstation, the only place anyone edits it. The dashboard there copies each valid profile into its Consul KV, under `lvh-yard/route-profiles/<name>`. Every Workstation that routes a Publication naming it reads it from there, as it already reads Publications, and gives it to its Traefik through the HTTP provider as `<workstation>_<profile>`. So a Service is protected the same way on every Workstation, and nobody copies Traefik configuration by hand.

## Considered Options

- **Each Workstation applies its own file's profile of the same name**: rejected. Files drift apart, and a missing one leaves a Service open or unrouted.
- **Profiles only on the Home Workstation (Traefik's file provider)**: chosen first, then rejected. Other Workstations, and their Aliases, would route the Service without its auth.
- **Syncing the files with git, Syncthing or a `profiles pull` command**: rejected. It needs a tool outside LVH Yard, or goes stale.

## Consequences

- A profile must work on every Workstation. The dashboard refuses `basicAuth.usersFile`, `forwardAuth` to an out-of-range address, and plugins. A chain may name only middlewares of the same file, and they travel with it.
- A broken file keeps the last good profiles in KV and shows an error; a bad save never deletes one.
- A Publication naming a profile that does not exist fails closed: Traefik refuses its Route.
- Profiles, basic-auth hashes included, are readable by the whole Yard. Keeping secrets elsewhere, such as Vault, is a later study.
- An auth profile protects only the hostname. A direct connection to the Instance's address skips it, so LVH Yard warns that the Service must check its users itself.
