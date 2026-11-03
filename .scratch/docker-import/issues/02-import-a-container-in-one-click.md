# 02: Import a container in one click

**What to build:** One Import click on a single-port container gives it a hostname: the Service is created (or reused), the container bound, and the Service published with Local Scope.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] `POST /api/workstation/containers/{container}/import` binds by Compose project and service when present, else by container name
- [ ] The Service is named after the Compose service or the container
- [ ] A new Service is published Local; an existing Service keeps its Publication and Scope, or stays unpublished
- [ ] The container leaves the list after import
- [ ] Errors (name rule, Core Service name) show next to the container
