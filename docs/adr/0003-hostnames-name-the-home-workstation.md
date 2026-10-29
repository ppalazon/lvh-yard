# Hostnames of Remote Services name their Home Workstation

A Workstation's own Services are reached at `<service>.<base-domain>`, and Remote Services at `<service>.<workstation>.<base-domain>`. Coworkers often run Services with the same name, such as `api`. So one Yard-wide name per Service would force clashes, or would merge different code into one Service. The price is that the same Service has a different hostname on its Home Workstation than on the others. An Alias can give a Remote Service a short name on one Workstation.

## Consequences

- Workstation names are unique in the Yard and are single DNS labels.
- HTTPS needs a certificate for `*.<base-domain>` plus one for `*.<workstation>.<base-domain>` per Workstation, because a wildcard covers only one level.
