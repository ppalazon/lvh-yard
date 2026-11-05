# 04: The Backups folder and the safety Backup

**What to build:** Each Workstation has a `backups/` folder, and each import that changes something first writes a safety Backup there. See `.scratch/backups/spec.md`, user stories 11, 41, 42, 56 to 58.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `compose.yaml` mounts the `backups/` folder of the Workstation Folder read-write into the dashboard and sets `YARD_BACKUPS` to its path.
- [ ] The read-only mount of the whole Workstation Folder goes away.
- [ ] `lvh-yard init` and `reinit` create `backups/` before Compose starts.
- [ ] Before the first write of an import that changes something, the dashboard writes `<workstation>-<yyyyMMdd-HHmmss>-before-import.yaml` with mode `0600`.
- [ ] An import that changes nothing writes no safety Backup.
- [ ] The API answer and the CLI output give the safety Backup name.
- [ ] Dashboard tests point `YARD_BACKUPS` at a temporary folder and cover the file, its mode and the case with no change.
- [ ] CLI tests cover `init` and `reinit` with the folder and the mount, and the printed name.
- [ ] `docs/user-guide.md` explains the folder and the safety Backup.
