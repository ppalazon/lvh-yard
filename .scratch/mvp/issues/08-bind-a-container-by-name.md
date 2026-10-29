# 08: Bind a Service to a container by name

**What to build:** A developer binds a Service to a Docker container by its name and a container port, from the Register form or with `yard register --container`. While a container with that name runs, the Service has a Bound Instance at its current address and port. When the container stops, the Bound Instance goes away. The dashboard tests start real containers.

**Blocked by:** 01 (Register a Static Instance and see it in the Catalog), 02 (CLI: `yard list`, `yard register`, `yard remove`)

**Status:** ready-for-agent

- [ ] A Container Binding names a container and a container port, and is stored in this Workstation's Consul KV.
- [ ] When a matching container starts, a Bound Instance is registered without any action from the developer.
- [ ] When the container stops or is removed, its Bound Instance is removed.
- [ ] When the container port is published on the host, the Bound Instance uses the LAN address and the published host port.
- [ ] When it is not published, the Bound Instance uses the container's own address and the container port.
- [ ] A Bound Instance gets the same default TCP check as a Static Instance.
- [ ] On startup, the dashboard brings every Container Binding in step with the containers running now.
- [ ] The Service detail page and the API show each Container Binding, including one that matches no container.
- [ ] Removing a Container Binding removes its Bound Instances.
- [ ] The Register form and `yard register --container` add a Container Binding, and `yard remove` removes one.
