# Import Docker containers as Services

Status: done

Words follow `GLOSSARY.md` (Container Binding, Bound Instance, Instance Priority). Builds on the MVP's Container Bindings.

## Problem Statement

To give a running container a hostname, the user must know its container name or Compose project and service, and its container port, then type them into the register form or `lvh-yard register`. They cannot see which of their running containers LVH Yard does not follow yet.

## Solution

A `/containers` page lists the running containers on this Workstation that no Container Binding covers, grouped by Compose project, with their ports and Docker HEALTHCHECK state. One Import click binds the container (by Compose service when it has one, else by container name) on its port, into a Service of the same name, and publishes it with Local Scope. A container with more than one port asks which one. If the Service already exists, the import adds the binding to it and keeps its Publication. `lvh-yard containers` and `lvh-yard import` do the same from the terminal.

## User Stories

1. As a user, I want a page listing my running containers that LVH Yard does not follow yet, so that I can see what I could give a hostname.
2. As a user, I want containers grouped by Compose project, so that I find a project's services together.
3. As a user, I want containers without a Compose project listed apart, so that `docker run` containers are shown too.
4. As a user, I want each container's ports shown (container port, and host port when published), so that I pick the right one.
5. As a user, I want each container's Docker HEALTHCHECK state shown when it has one, so that I know whether it is ready.
6. As a user, I want one Import click to create the Service, bind the container and publish it locally, so that `http://<service>.<base-domain>` works at once.
7. As a user, I want a Compose container imported by Compose project and service, so that the binding survives `docker compose up` recreating the container.
8. As a user, I want a plain container imported by its name, so that it is followed while that name runs.
9. As a user, I want the Service named after the Compose service, or the container name, so that I do not have to name it.
10. As a user, I want to change the proposed Service name before importing, so that I can avoid a clash or use a nicer name.
11. As a user, I want a container with one port imported on that port, so that one click is enough.
12. As a user, I want a container with several ports to ask me which one, so that the Route goes to the right port.
13. As a user, I want a container with no exposed port to ask me for a port, so that I can still import it.
14. As a user, I want importing into an existing Service to add the binding and keep its Publication and Scope, so that the Docker copy joins my terminal copy under one hostname.
15. As a user, I want importing into a Service that is unpublished to keep it unpublished, so that my choice stays.
16. As a user, I want a container already covered by a Container Binding left off the list, so that I do not import it twice.
17. As a user, I want the list to refresh by itself as containers start and stop, so that it stays current.
18. As a user, I want the warning about an unpublished container port shown when an import into a Yard-scoped Service has one, so that I know other Workstations cannot reach it.
19. As a user, I want an import that fails (name not allowed, Core Service name) to show the reason next to the container, so that I can fix it.
20. As a user, I want to land on the Service's page after importing, or stay on the list with a confirmation, so that I can keep importing.
21. As a user, I want `lvh-yard containers` to list the same containers and ports, so that I can import from the terminal.
22. As a user, I want `lvh-yard import <container> [--port N] [--name S]` to import one, so that scripts can do it.
23. As a user, I want `lvh-yard import` without `--port` on a multi-port container to fail and list the ports, so that I know what to pass.
24. As a user, I want `lvh-yard import --no-publish` to bind without publishing, so that I can import without a hostname.
25. As a user, I want the dashboard to show a clear message on the page when it cannot read Docker, so that I know why the list is empty.

## Implementation Decisions

- A new Container Import module lists running containers from Docker, removes those matched by any existing Container Binding of this Workstation, and returns for each: name, Compose project and service when labelled, ports (container and host), and HEALTHCHECK state.
- Import is one operation: register (or reuse) the Service, add a Container Binding (Compose project and service when present, else container name) on the chosen container port, and publish with Local Scope unless the Service already has a Publication, or `publish: false` is asked.
- API: `GET /api/workstation/containers` lists the containers; `POST /api/workstation/containers/{container}/import` with `{ port, service?, publish? }` imports one and answers the Service. Port is required when the container has more than one port or none; the error lists the ports.
- Names follow the existing Service name rule; a Compose service name that breaks it is proposed in a cleaned form, and the user can edit it.
- Page `/containers` with a refreshed region; a link from the catalog page's header or navigation.
- CLI: `containers` and `import` subcommands.
- Refusals reuse the existing problem-details style. A Core Service name is refused (see Core Services).

## Testing Decisions

- A good test checks only what a user could see: API answers, pages, the Traefik configuration and CLI output.
- Seam 1, the dashboard, with real Docker and Consul from Testcontainers. Tests start small containers (one with a Compose label set, one plain, one with two ports, one with a HEALTHCHECK) and check: the list, grouping and ports; importing each kind; the resulting Container Binding, Bound Instance and Publication; importing into an existing Service; leaving covered containers off the list; the port-required error.
- Seam 2, the CLI, with the stub dashboard: `containers` output, `import` arguments and errors.
- The OpenAPI test covers the new endpoints.
- Prior art: the container bindings test and the register page test.

## Out of Scope

- Starting or stopping containers.
- Containers on other Workstations.
- Importing stopped containers.
