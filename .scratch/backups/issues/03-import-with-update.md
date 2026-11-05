# 03: Import a Backup with `--update`

**What to build:** You bring a Workstation back to a Backup, and the import overwrites what exists. See `.scratch/backups/spec.md`, user stories 7, 34 and 35.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `mode=update` overwrites a matched Publication, the health check, tags and meta of a Static Instance, the target of an Alias, and the definition of a Route Profile.
- [ ] A matched Container Binding is always a skip.
- [ ] A match with the same values is a skip.
- [ ] `publication: none` unpublishes an existing Service. A missing `publication` key keeps the existing Publication.
- [ ] The plan shows update for each overwrite.
- [ ] `lvh-yard import --update` sends `mode=update`.
- [ ] Dashboard tests cover each overwrite, each skip, and both `publication` cases. CLI tests cover `--update`.
- [ ] `docs/user-guide.md` explains the two modes.
