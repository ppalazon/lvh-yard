# 05: Private Workstation Folder, and Consul as your user

**What to build:** Only the user can read the Workstation Folder. The folder is mode 700 and every file in it is mode 600. Consul runs as the user and group of `.env`, and keeps its data in `consul-data/` inside the folder. A folder from an older version gets the right modes the next time a `ws` command runs.

**Blocked by:** 02: `ws init` and `ws upgrade` replace `init`

**Status:** ready-for-agent

Spec: [../spec.md](../spec.md)

- [ ] `ws init` makes every folder with mode 700 and every file with mode 600, `compose.yaml` and the backups folder included.
- [ ] `ws init` creates `consul-data/` as the user before Compose starts, and always fills `YARD_UID` and `YARD_GID`.
- [ ] `ws upgrade` and `ws set` fix the modes of an existing folder.
- [ ] The Consul service runs as `YARD_UID:YARD_GID` and mounts `consul-data/`. The `consul-data` named volume is gone.
- [ ] On a new Workstation, Consul starts with a Raft leader and the dashboard works, with the folder at 700 (shown by the integration test).
- [ ] CLI unit tests check the modes and the filled settings. `docs/user-guide.md` and `docs/development.md` describe the folder and its modes.
