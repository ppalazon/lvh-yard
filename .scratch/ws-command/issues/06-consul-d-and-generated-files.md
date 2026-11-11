# 06: Consul fixed options in `consul.d/`, and generated files

**What to build:** The options of Consul that do not depend on `.env` move from the shell command into `consul.d/00-lvh-yard.hcl`, which Consul reads with `-config-dir`. Traefik watches `traefik/dynamic/`, and the folder is empty for now. The three files that LVH Yard owns start with a "generated, do not edit" line, and `ws upgrade` rewrites them. Files that the user adds to those folders stay.

**Blocked by:** 05: Private Workstation Folder, and Consul as your user

**Status:** ready-for-agent

Spec: [../spec.md](../spec.md)

- [ ] `ws init` writes `consul.d/00-lvh-yard.hcl` and an empty `traefik/dynamic/`. Both are mounted read-only.
- [ ] The values that depend on `.env` stay flags of Consul. The Consul options that do not depend on it come from the file.
- [ ] Traefik reads `traefik/dynamic/` with the file provider and watch on, and its install options stay flags ([ADR-0011](../../../docs/adr/0011-consul-files-traefik-flags-and-owned-files.md)).
- [ ] `ws upgrade` rewrites `consul.d/00-lvh-yard.hcl` and keeps every other file in `consul.d/` and `traefik/dynamic/` byte for byte. It still edits only the image lines of `compose.yaml`.
- [ ] A generated file starts with a "generated, do not edit" line.
- [ ] Consul and Traefik of a new Workstation still start healthy (shown by the integration test).
- [ ] `docs/user-guide.md` says which files LVH Yard owns and which the user owns.
