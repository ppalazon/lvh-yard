# 01: Register a Static Instance and see it in the Catalog

**What to build:** A developer registers a Static Instance of a Service, through the REST API or the dashboard's Register form, and sees it in the Catalog and on the Service's detail page with its Health. They can remove it again. This is the first tracer bullet through Consul, the REST API, the pages and the tests, so it also sets up the dashboard tests to run against a real Consul (Testcontainers), and adds the OpenAPI contract test. See the spec, `.scratch/mvp/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Dashboard tests start a real Consul through Testcontainers, and the dashboard reads and writes it.
- [x] A committed `openapi.yaml` describes the REST API, and a test fails when it differs from what the running dashboard serves.
- [x] Registering a Static Instance with a Service name, a host and a port creates a registration on this Workstation's Consul agent.
- [x] The host defaults to this Workstation's LAN address.
- [x] More than one Static Instance of the same Service can be registered.
- [x] A Service name that is not a single DNS label (lowercase letters, digits and `-`) is refused with a client error that explains why.
- [x] The name `yard` is refused with a client error that explains why.
- [x] An Instance registered without a health check gets a TCP check on its port.
- [x] The Catalog page and the API list each Service with its Instances and their Health.
- [x] The Service detail page shows the Service's Instances and each one's Health.
- [x] The Register form adds a Static Instance and shows validation errors in the form.
- [x] Removing a Static Instance removes its registration. A Service with nothing left disappears from the Catalog.
- [x] A Static Instance stays registered, with critical Health, when nothing listens on its port.
