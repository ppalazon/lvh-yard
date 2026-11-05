# Route Profiles live in Consul KV and travel through it

A Route Profile is a Traefik middleware in Traefik's own format. Its Home Workstation keeps it in its own Consul KV, under `lvh-yard/route-profiles/<name>`, and that is its only copy. You add, change and remove a Route Profile through the dashboard of its Home Workstation: from the Route Profiles page, from the CLI, or with a Backup import. The dashboard refuses a Route Profile that does not work on every Workstation, so Consul KV holds only valid ones. Every Workstation that routes a Publication naming it reads it from there, as it reads Publications. It gives the profile to its Traefik through the HTTP provider as `<workstation>_<profile>`. So a Service is protected the same way on every Workstation, and nobody copies Traefik configuration by hand.

## Considered Options

- **A `route-profiles.yaml` file in the Workstation Folder that the dashboard copies into Consul KV**: chosen first, then rejected. Only the dashboard reads Consul KV, so the file was a second copy to keep in step. The dashboard polled it and showed errors for refused profiles. When the file broke, the dashboard kept the last good profiles. A Backup import was not able to write Route Profiles, because the dashboard sees the Workstation Folder as read-only.
- **Each Workstation applies its own file's profile of the same name**: rejected. Files drift apart, and a missing one leaves a Service open or unrouted.
- **Profiles only on the Home Workstation (Traefik's file provider)**: rejected. Other Workstations, and their Aliases, then route the Service without its auth.
- **Syncing the files with git, Syncthing or a `profiles pull` command**: rejected. It needs a tool outside LVH Yard, or goes stale.

## Consequences

- A profile must work on every Workstation. The dashboard refuses `basicAuth.usersFile`, `forwardAuth` to an out-of-range address, and plugins. A chain can name only middlewares of the same Workstation, and they travel with it.
- A refused save changes nothing, so there is no "last good" copy and no stored error.
- A Publication naming a profile that does not exist fails closed: Traefik refuses its Route.
- A Backup holds the Route Profiles of its Workstation.
- Profiles, basic-auth hashes included, are readable by the whole Yard. Keeping secrets elsewhere, such as Vault, is a later study.
- An auth profile protects only the hostname. A direct connection to the Instance's address skips it, so LVH Yard warns that the Service must check its users itself.
