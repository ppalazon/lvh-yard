# 05: Bind a container name or a Compose service from the Containers page and the CLI

**What to build:** The Containers page gets a binding form, and the CLI gets `register container --compose`. The bare `lvh-yard register <service>` goes away. Adding a Binding can publish a new Service, and a followed target is refused. See `.scratch/navigation-and-register/spec.md`, user stories 23 to 26, 28 to 31, 35, 37 and 38.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] `POST /api/workstation/services/{service}/container-bindings` takes an optional `publish` boolean, with the same rule as Static Instances.
- [ ] If a Binding of this Workstation already has the same container name, or the same Compose project and service, that endpoint answers 409. The port does not matter. The answer names the Service.
- [ ] The Containers page has a form below the Unfollowed Containers list. It chooses "Container name" or "Compose service" and has the port, the Service name and a "Publish a new Service" checkbox.
- [ ] On success, the form goes to the Service's page. On failure, the page answers 400 and shows the reason next to the field.
- [ ] `lvh-yard register container --compose <project>/<service> --port <port>` binds a Compose service. Its Service name defaults to the cleaned Compose service. It takes `--service` and `--no-publish`.
- [ ] A positional container together with `--compose` is refused.
- [ ] The bare `lvh-yard register <service>` does not exist. `lvh-yard register` shows its two subcommands.
- [ ] `openapi.yaml` and the user guide show the form, the `--compose` option and the 409 answer.
- [ ] `ContainerBindingsTest`, `ContainersTest` and the CLI test cover these points.
