# 15: `dev:up` uses `ws`

**What to build:** The two test Workstations are real Workstation Folders, made with the same commands a user runs. Development covers the real path, and each test Workstation has its own files for TLS later.

**Blocked by:**

- 04: `ws show`, `ws set` and `ws join`
- 05: Private Workstation Folder, and Consul as your user

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `mise run dev:up` makes `dev/ws-a/` and `dev/ws-b/` with `ws init` and `ws set`, with the ports and Base Domains that `dev/ws-a.env` and `dev/ws-b.env` hold today (ws-b uses `localhost`), then starts both with `ws start`.
- [x] `dev:down` and `dev:dashboard` work with the new folders.
- [x] `dev/*.env` is deleted, and the new folders are git-ignored.
- [x] The two Workstations federate, and the dashboards work at the same addresses as before.
- [x] `docs/development.md` describes the new flow.
