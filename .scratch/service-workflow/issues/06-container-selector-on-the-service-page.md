# 06: The container selector on the Service page

**What to build:** On the Service page, you bind a container or a Compose service with a list that you can also type in. The Containers page goes away. See `.scratch/service-workflow/spec.md`, user stories 19 to 29, 59 and 72.

**Blocked by:** 01

**Status:** ready-for-agent

- [x] The container list API returns every container of this Workstation, stopped ones included. Each entry has its Compose project and service, its Container Ports, and whether a Container Binding follows it.
- [x] The Service page has a "Bind container" form. The container field lists Compose services as project and service, then containers with no Compose project by name.
- [x] You can type a container name or a Compose project and service that does not exist.
- [x] The port field lists the Container Ports of the pick and accepts a typed port.
- [x] A followed target shows but cannot be selected. A typed followed target is refused at its field.
- [x] A Service can have many Container Bindings. Each shows its Bound Instances.
- [x] The Containers page, its routes and the one-click register endpoint go away, with no redirects. The menu loses its link.
- [x] `lvh-yard containers` keeps listing the Unfollowed Containers.
- [x] Component tests cover the list API, both groups, a typed target, the port field, a followed target and the removed page. The OpenAPI test covers the changed endpoints.
- [x] If 03 and 05 are done, the Service page parts follow the order of the spec.
- [x] `docs/user-guide.md` explains the container selector.
