# 07: `lvh-yard.yaml` for the dashboard

**What to build:** `ws init` writes `lvh-yard.yaml` in the Workstation Folder, with every line commented out and the dashboard's own defaults shown. The dashboard container reads it, so a structured setting has a home next to `.env`. The user owns the file, and `ws` never edits it.

**Blocked by:** 02: `ws init` and `ws upgrade` replace `init`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `ws init` writes `lvh-yard.yaml` with mode 600, every line commented out.
- [x] The dashboard container mounts it read-only where Spring Boot looks for an external config file, and starts with no change in behaviour.
- [x] A value that the user uncomments in `lvh-yard.yaml` changes the dashboard after a restart, and `.env` values still win through their placeholders (shown by a component or integration test).
- [x] Compose keeps its explicit `environment:` list. No `env_file` hands the Gossip Key to the dashboard.
- [x] `ws upgrade` and `ws set` never change the file.
- [x] A dashboard run from the sources is unchanged. `docs/user-guide.md` describes the file.
