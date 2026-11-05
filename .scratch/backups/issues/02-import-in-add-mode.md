# 02: Import a Backup in add mode

**What to build:** You import a Backup, and the dashboard adds what is new and skips what exists. A wrong file changes nothing. See `.scratch/backups/spec.md`, user stories 4 to 6, 8 to 10, 12 to 18, 30 to 33, 36, 38 to 40, 60 and 61.

**Blocked by:** 01

**Status:** done

- [x] `POST /api/workstation/backup` takes a Backup as `application/yaml` with `mode=add` and an optional `dry-run`, and answers the plan.
- [x] Each plan entry has the kind, the name, and add or skip. A skip has a reason.
- [x] The dashboard checks the whole file before any write, with the same rules as the API. Unknown keys, an unknown version and unknown Route Profiles are refused.
- [x] A refused file answers a client error problem that lists each problem with its place in the file. Nothing changes.
- [x] Matching follows the spec: Service by name, Static Instance by host and port inside its Service, Container Binding by ID, Alias and Route Profile by name.
- [x] A missing host means the LAN address of this Workstation. A new Service without a `publication` key stays Unpublished.
- [x] A Publication can name a Route Profile from the same file.
- [x] An Alias whose target is this Workstation is refused.
- [x] The writes go in this order: Route Profiles, Static Instances and Container Bindings, Publications, Aliases. If a write fails, the import stops and reports what it applied.
- [x] `lvh-yard import <file>` and `lvh-yard import -` send the file and print one line per plan entry. `--dry-run` changes nothing.
- [x] Dashboard tests cover a round trip, each match rule, each refusal and the dry run. They also cover a Backup from the remote Workstation and the Alias to this Workstation.
- [x] CLI tests cover a file, standard input, `--dry-run`, the plan lines and the exit codes.
- [x] The OpenAPI test covers the endpoint. `docs/user-guide.md` explains import in add mode.
