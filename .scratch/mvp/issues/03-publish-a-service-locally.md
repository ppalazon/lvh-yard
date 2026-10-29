# 03: Publish a Service on its own Workstation

**What to build:** A developer publishes a Service from its detail page or with `yard publish`, and reaches it at `<service>.<base-domain>` through Traefik. Unpublishing removes the hostname without touching any Instance. Every Service shows its state. In this ticket, a Publication always has Local Scope.

**Blocked by:** 01 (Register a Static Instance and see it in the Catalog), 02 (CLI: `yard list`, `yard register`, `yard remove`)

**Status:** ready-for-agent

- [ ] Publishing a Service stores its Publication, with Local Scope, in this Workstation's Consul KV.
- [ ] Traefik's configuration from the dashboard has a Route for `<service>.<base-domain>` to every registered Instance of a published Service.
- [ ] Instances registered after the Publication are routed without publishing again.
- [ ] Unpublishing removes the Route and leaves every Instance registered.
- [ ] The `yard` Route is always present, and no Service can replace it.
- [ ] The Catalog, the Service detail page, the API and `yard list` show each Service as Unpublished, Unavailable, Unhealthy, or published and healthy.
- [ ] A published Service with no Instances shows as Unavailable, and one whose Instances have no passing Health shows as Unhealthy.
- [ ] `yard publish` and `yard unpublish` do the same as the page's controls.
