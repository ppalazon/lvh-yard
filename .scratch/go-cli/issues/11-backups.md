# 11: Backups

Status: ready-for-agent

Blocked by: 03

Spec: `../spec.md`.

## What to build

A user exports the setup of a Workstation and imports it into the same Workstation or another one.

- `export`: prints a Backup as YAML. `-o <file>` writes it to a file that only the user can read. The CLI does not parse the YAML.
- `import <file>`: adds what is new in a Backup and skips what exists. A dry run shows the plan and changes nothing. A refused import shows the dashboard's reasons. See `Export.java` and `Import.java`.
- The client methods that send and read the Backup with the YAML content type.

Port the cases of `BackupTest` that test CLI behavior.

## Acceptance criteria

- [ ] On a real test Yard, `export` from ws-a, then `import` on ws-b, makes the same Services with their Publications, Instance Sources, Instance Priority and Route Profiles.
- [ ] `export -o` writes a file with mode 600.
- [ ] A dry run of `import` changes nothing.
- [ ] `mise run check` passes, and the openapi test knows the new calls.
