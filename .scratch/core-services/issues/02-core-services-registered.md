# 02: Core Services registered on every start

**What to build:** On a new Workstation, `yard.<base-domain>`, `consul.<base-domain>` and `traefik.<base-domain>` work at once, and the catalog lists the three Core Services with their Health.

**Blocked by:** 01

**Status:** done

- [ ] On start the dashboard ensures `yard`, `consul` and `traefik`: a Static Instance with a fixed ID, an HTTP check (actuator health, `/v1/status/leader`, `/ping`; 10s, 2s) and a Local Publication
- [ ] Anything missing is put back on the next start; a second start changes nothing
- [ ] Instances use the ports and bind addresses from settings; a dashboard on `0.0.0.0` is registered at `127.0.0.1`
- [ ] Consul not ready at start is retried, not fatal
- [ ] The dashboard's hostname still routes when the `yard` Core Service is missing
- [ ] A `core` flag is in the API (and `openapi.yaml`), on the catalog and Service pages, and in `lvh-yard list`
- [ ] A teammate's Core Services are shown, marked, with no Route
