# 01: List running containers LVH Yard does not follow yet

**What to build:** The user opens `/containers` and sees their running containers that no Container Binding covers, grouped by Compose project, with ports and HEALTHCHECK state. See `.scratch/docker-import/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `GET /api/workstation/containers` lists name, Compose project and service, ports (container and host) and HEALTHCHECK state, in `openapi.yaml`
- [ ] Containers matched by any Container Binding of this Workstation are left out
- [ ] Plain containers are listed apart from Compose projects
- [ ] The page refreshes by itself and is linked from the catalog
- [ ] A Docker read failure is explained on the page
