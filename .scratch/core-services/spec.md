# Core Services: the dashboard, Consul and Traefik as Services

Status: done

Words follow `GLOSSARY.md` (Core Service). Decisions follow ADR-0002 (Traefik reads its Routes from LVH Yard).

## Problem Statement

On a new Workstation the catalog is empty. The dashboard is reached at `yard.<base-domain>` through a fixed Route that is not a Service, so it has no Health. Consul's web page is only at `127.0.0.1:8500`, and Traefik's own admin page is turned off. A user cannot see, from LVH Yard itself, whether the pieces LVH Yard runs on are healthy, and has no hostnames for them.

## Solution

The dashboard keeps three Core Services registered and published on its Workstation: `yard` (the dashboard), `consul` (Consul's web page) and `traefik` (Traefik's admin page). Each has a Static Instance on loopback with an HTTP check, and a Publication with Local Scope. On every start the dashboard makes sure they exist, and puts back any part that was removed. Traefik's admin page is turned on, on its own loopback entrypoint. A Core Service cannot be removed, unpublished, or given Yard Scope; the only change allowed is its Route Profiles.

## User Stories

1. As a new user, I want `yard.<base-domain>`, `consul.<base-domain>` and `traefik.<base-domain>` to work as soon as the Workstation starts, so that I can open the dashboard, Consul's page and Traefik's page without knowing their ports.
2. As a user, I want the three Core Services listed in the catalog, so that I see their Health like any other Service.
3. As a user, I want each Core Service marked as a Core Service in the catalog, on its page, in the API and in `lvh-yard list`, so that I know LVH Yard manages it.
4. As a user, I want `yard` to have an HTTP check on the dashboard's health endpoint, so that I see when the dashboard is not healthy.
5. As a user, I want `consul` to have an HTTP check on Consul's leader endpoint, so that I see when Consul has no leader.
6. As a user, I want `traefik` to have an HTTP check on Traefik's ping endpoint, so that I see when Traefik is not running.
7. As a user, I want the checks to run every 10 seconds with a 2 second timeout, so that they match the defaults of other Services.
8. As a user, I want the dashboard to register any missing Core Service, Instance, check or Publication on every start, so that a Workstation repairs itself.
9. As a user, I want a Core Service that already exists left as it is when the dashboard starts, so that starting twice changes nothing.
10. As a user, I want the dashboard to refuse to remove a Core Service's Instance, with a clear reason, so that I cannot break my own Workstation by mistake.
11. As a user, I want the dashboard to refuse to unpublish a Core Service, with a clear reason, so that its hostname never disappears.
12. As a user, I want the dashboard to refuse Yard Scope for a Core Service, explaining that its Instance is on loopback and other Workstations could not reach it, so that I do not publish something that cannot work.
13. As a user, I want to add Route Profiles to a Core Service, for example basic auth on `consul`, so that I can protect it.
14. As a user, I want the Service page of a Core Service to hide the remove, unpublish and Scope controls, so that I only see what I can do.
15. As a user, I want `lvh-yard remove`, `unpublish` and `publish --scope yard` on a Core Service to print the dashboard's reason and exit with 1, so that scripts see the refusal.
16. As a user, I want no other Service to take the name `yard`, `consul` or `traefik`, so that the Core Services' hostnames never clash.
17. As a user, I want the Core Services' Instances to use the ports and bind addresses from my `.env`, so that they work after I change a port.
18. As a user with the dashboard on `0.0.0.0`, I want the `yard` Instance at `127.0.0.1`, so that its check and Route still work.
19. As a user on another Workstation, I want to see a teammate's Core Services as Remote Services, without a hostname, so that I know which Workstations run LVH Yard and how healthy they are.
20. As a user, I want Traefik's admin page on its own port on loopback, so that it is never open to the LAN by default.
21. As a user, I want `YARD_TRAEFIK_ADMIN_PORT` to change that port, so that I can avoid a clash.
22. As a user upgrading from an earlier version, I want the old fixed `yard` Route replaced by the `yard` Core Service without a gap, so that the dashboard's hostname keeps working.

## Implementation Decisions

- A new module, Core Services, runs once when the dashboard starts. It ensures each Core Service's Static Instance (with a fixed, known Instance ID, so it is found again), its HTTP check and its Local Publication, writing only what is missing. Failures to reach Consul at start are retried, not fatal.
- Instances: `yard` at `127.0.0.1` (or the dashboard's bind address when it is not `0.0.0.0`) and `YARD_DASHBOARD_PORT`; `consul` at `127.0.0.1:YARD_CONSUL_HTTP_PORT`; `traefik` at `127.0.0.1:YARD_TRAEFIK_ADMIN_PORT` (default 8080).
- Checks: `yard` on the dashboard's actuator health endpoint; `consul` on `/v1/status/leader`; `traefik` on `/ping`. Interval 10s, timeout 2s.
- Core Service is marked in Consul service metadata, in LVH Yard's reserved metadata, so every Workstation reading the catalog knows it. The API's Service and Instance models get a `core` flag; `lvh-yard list` marks it.
- Write paths refuse changes to a Core Service with a client error and an explanation (the same problem-details style as Remote Service refusals): removing its Instances or Container Bindings, unpublishing it, Yard Scope, and registering new Instances under its name. Setting its Route Profiles is allowed.
- The names `yard`, `consul` and `traefik` are reserved for Core Services; registering another Service with one is refused.
- The Route builder no longer adds a fixed Route for the dashboard; the `yard` Core Service's Publication gives it. If the `yard` Core Service is missing (for example, Consul is not ready yet), the Route builder still adds the dashboard's Route, so the dashboard is never unreachable through its hostname.
- `compose.yaml` (and the `init` template): Traefik gets an `admin` entrypoint on `127.0.0.1:${YARD_TRAEFIK_ADMIN_PORT:-8080}`, with `--api.dashboard=true`, `--api.insecure=false` served on that entrypoint, and `--ping=true` on it. `.env.example` gains `YARD_TRAEFIK_ADMIN_PORT`, and the dashboard reads it.
- `consul.<base-domain>` routes to Consul's whole HTTP API, which has no login; this is acceptable on loopback-only Local Scope and is why Yard Scope is refused.

## Testing Decisions

- A good test drives the dashboard over HTTP and checks only what a user could see: API answers, pages and the Traefik configuration it serves. It never reads Consul keys or calls internal classes.
- Seam 1, the dashboard, with real Consul from Testcontainers. Tests check that the three Core Services exist with their checks and Local Publications after start; that starting again changes nothing; that a removed Instance or Publication (removed straight in Consul by the test's setup, the only way to do it) is back after the next start; that every refused change answers a client error with a reason; that Route Profiles can be set; and that the Traefik configuration has Routes for the three hostnames.
- One test with the two-datacenter Yard checks that a remote Workstation's Core Services are shown, marked, and get no Route.
- Seam 2, the CLI, with the stub dashboard: `lvh-yard list` marks Core Services, and refused commands print the reason and exit 1.
- The OpenAPI test covers the new `core` flag and the refusals.
- Prior art: the publication and static instance tests, the remote services test, and the Traefik configuration test.

## Out of Scope

- Yard Scope for Core Services.
- Any login for Consul or Traefik beyond what Route Profiles give.
- Core Services for Docker itself.

## Further Notes

- This replaces the grill's first idea of seeding only "when Consul's data is empty": that is hard to detect, and one Static Instance would block the seeding.
