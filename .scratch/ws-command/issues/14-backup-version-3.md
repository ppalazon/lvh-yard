# 14: Backup version 3

**What to build:** A Backup holds the new kind of Route Profile, so an import brings it back. Backups of version 2 still import.

**Blocked by:** 13: The Client Certificate Route Profile

**Status:** ready-for-agent

Spec: [../spec.md](../spec.md)

- [ ] An export writes `version: 3`, and holds the Route Profiles of the new kind.
- [ ] An import of version 3 brings back the new kind, in every mode of the import.
- [ ] An import of a version 2 file works as before.
- [ ] The safety Backup of an import has version 3.
- [ ] Unit and component tests cover the format, the export, the import and the version 2 import. `docs/user-guide.md` shows the new version.
