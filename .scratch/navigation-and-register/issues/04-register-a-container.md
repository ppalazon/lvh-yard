# 04: `lvh-yard register container` replaces Import

**What to build:** The import endpoint becomes a register endpoint that also binds a stopped container by name. `lvh-yard register container <container>` calls it, and `lvh-yard import` goes away. See `.scratch/navigation-and-register/spec.md`, user stories 15 to 22, 27, 32 and 36.

**Blocked by:** 03

**Status:** done

- [x] `POST /api/workstation/containers/{container}/import` becomes `POST /api/workstation/containers/{container}/register`, with the body `{port, service, publish}`.
- [x] If the container runs, the endpoint acts as Import does today.
- [x] If the container does not run, the endpoint binds it by name. Then `port` is required, and the Service name defaults to the cleaned container name.
- [x] A stopped container without a port answers 400 with field `port`.
- [x] A container that a Binding of this Workstation follows answers 409 and names the Service.
- [x] `lvh-yard register container <container>` calls the endpoint. It takes `--port`, `--service` and `--no-publish`.
- [x] `lvh-yard import` does not exist.
- [x] The one-click button of the Containers list is labeled "Register" and posts to `/containers/{container}/register`.
- [x] `openapi.yaml` and the user guide show the new endpoint and command, and never say "import".
- [x] `ContainersTest` and the CLI test cover a running container, a stopped container and the refusals.
