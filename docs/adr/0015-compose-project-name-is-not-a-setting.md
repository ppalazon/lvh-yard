---
status: accepted
---

# The Compose project name is not a setting

`lvh-yard ws init` does not write `COMPOSE_PROJECT_NAME`, and the `.env` templates do not list it. Compose names the project after the Workstation Folder. This replaces one sentence of [ADR-0014](0014-windows-workstations-use-docker-desktop.md), which had `ws init` write a project name made from the folder name and a code of its path.

A written project name is easy to break. Changing it selects a different `consul-data` volume, so the Workstation looks empty.

Each Workstation Folder must have its own name, or the folders share one volume. The user guide says so.
