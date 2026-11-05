# 01: Export a Backup

**What to build:** You save the setup of your Workstation to a YAML file. See `.scratch/backups/spec.md`, user stories 1 to 3, 19 to 29, 37 and 59.

**Blocked by:** Route Profiles in Consul KV, ticket 02 (`.scratch/route-profiles-in-kv/issues/02-route-profiles-change-through-the-api.md`)

**Status:** done

- [x] `GET /api/workstation/backup` answers a Backup as `application/yaml`, with `version: 1` and `exported-from`.
- [x] The Backup holds every Service of this Workstation with its Publication, Static Instances and Container Bindings, plus Aliases and Route Profiles.
- [x] An Unpublished Service has `publication: none`.
- [x] A Static Instance holds its port, health check, tags and meta. Empty tags and meta are left out.
- [x] If a host equals the LAN address of this Workstation, the export leaves it out.
- [x] The Backup holds no Bound Instances, Health or Remote Services. LVH Yard's own Services are in it like others.
- [x] `lvh-yard export` prints the Backup. `-o <file>` writes it to a file with mode `0600`.
- [x] Dashboard tests cover each kind of entry and each rule above. CLI tests cover standard output and `-o`.
- [x] The OpenAPI test covers the endpoint.
- [x] `docs/user-guide.md` explains export and the secrets in a Backup.
