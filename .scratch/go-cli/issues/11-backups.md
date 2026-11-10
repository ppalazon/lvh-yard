# 11: Backups

Status: done

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
- [x] `export -o` writes a file with mode 600.
- [x] A dry run of `import` changes nothing.
- [ ] `mise run check` passes, and the openapi test knows the new calls.

## Comments

- 2026-11-10: The integration owner reported that `go vet`, all Go tests and the build passed.
- On the live two-Workstation Yard, file export used mode 0600 for both new and existing files. Standard output preserved the raw Backup text.
- The live import dry run left the setup snapshot unchanged. Import, repeated import and an update dry run passed.
- The integration owner will check the broader Backup transfer of Container Bindings, Aliases and Instance Priority after those commands are integrated. The full cross-Workstation acceptance item stays unchecked until that check.
- The shared client keeps HTTP status for safe repeated Service deletion and returns response bytes with errors for import failure plans.
- `mise run check` and hosted checks remain unverified. No tests, builds, linters or formatters ran during integration.
