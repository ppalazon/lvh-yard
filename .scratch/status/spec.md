# Status of this Workstation's LVH Yard

Status: done

Words follow `GLOSSARY.md`. Builds on `.scratch/core-services/spec.md` (Core Services and Traefik's ping).

## Problem Statement

When a hostname does not work, the user cannot tell which piece is at fault: Consul, Traefik, Docker, or the Service itself. The dashboard shows Services, but not whether Consul has a leader, which teammates' Workstations are federated, whether Traefik still fetches its Routes, or whether the dashboard can read Docker. A script cannot ask either.

## Solution

A `/status` page, a `GET /api/status` endpoint and `lvh-yard status` show the health of this Workstation's LVH Yard: Consul (answers, leader, the Workstations in its WAN pool), Traefik (ping, last fetch of its configuration), Docker (socket reachable, version) and each Core Service's Health. Every page's header shows four small coloured dots, one per part, refreshed every 5 seconds, that link to `/status`. `lvh-yard status` exits with 1 when any part is not OK.

## User Stories

1. As a user, I want a status page, so that I can see at once whether LVH Yard on this Workstation works.
2. As a user, I want to see whether Consul answers, so that I know the registry is up.
3. As a user, I want to see whether Consul has a leader, so that I know it can accept changes.
4. As a user, I want to see which Workstations are in Consul's WAN pool and whether each is alive, so that I know which teammates I am federated with.
5. As a user, I want a peer from my `.env` that is missing from the WAN pool shown as a problem, so that I see a federation that failed.
6. As a user, I want to see whether Traefik answers its ping, so that I know the proxy runs.
7. As a user, I want to see when Traefik last fetched its configuration from the dashboard, so that I know my Routes are current.
8. As a user, I want a last fetch older than a few poll intervals shown as a warning, so that I notice Traefik reading from another dashboard or none.
9. As a user, I want to see whether the dashboard can reach the Docker socket, so that I know Container Bindings work.
10. As a user, I want to see Docker's version, so that I can report it with a bug.
11. As a user, I want a Docker socket that cannot be read explained (missing, or permission denied with the group to add), so that I can fix it.
12. As a user, I want to see each Core Service's Health, so that I know the dashboard, Consul's page and Traefik's page answer through their checks.
13. As a user, I want each part shown as OK, warning or failing, with a short reason, so that I know what to do.
14. As a user, I want the status page to refresh by itself, so that I can watch a fix take effect.
15. As a user, I want four status dots in the header of every page, so that I notice a problem without opening the status page.
16. As a user, I want each dot to show its part's name on hover and to be readable without colour, so that I can tell them apart.
17. As a user, I want the dots to link to the status page, so that I can see the details in one click.
18. As a user, I want the header dots to refresh every 5 seconds, so that they stay current while I work.
19. As a user, I want a slow or failed part never to slow down the page I asked for, so that a broken Consul does not freeze the dashboard.
20. As a user, I want `GET /api/status` to return the same information as JSON, so that I can build on it.
21. As a user, I want `lvh-yard status` to print each part and its reason, so that I can check from the terminal.
22. As a user writing scripts, I want `lvh-yard status` to exit with 0 when every part is OK and 1 otherwise, so that I can wait for LVH Yard to be ready.
23. As a user, I want warnings alone not to fail `lvh-yard status`, unless I pass `--strict`, so that a stale fetch does not break my scripts by default.
24. As a user, I want `lvh-yard status` to say clearly when the dashboard itself does not answer, so that I know to start it.

## Implementation Decisions

- A new Status module gathers four parts: Consul, Traefik, Docker and Core Services. Each part is OK, warning or failing, with a reason and details. The overall status is the worst part.
- Consul: leader (`/v1/status/leader`), WAN members (`/v1/agent/members?wan=1`) and their state, compared with the configured peers when known.
- Traefik: `/ping` on the admin entrypoint; last fetch is the time the dashboard last served the Traefik configuration endpoint, which the dashboard records. Older than three poll intervals (15s) is a warning.
- Docker: a ping and version call through the existing Docker client; a failure says whether the socket is missing or not readable.
- Core Services: each one's Health from the catalog; missing is failing.
- Each part is read with a short timeout, in parallel, and the result is cached for about 5 seconds, so many open tabs cost one round of calls.
- API: `GET /api/status` returns `{ overall, parts: [{ name, state, reason, details }] }`, with state in `ok | warning | failing`. Documented in `openapi.yaml`.
- Pages: a `/status` page with a refreshed region, and a `/status/summary` fragment. The page layout's header loads the fragment through HTMX every 5 seconds, the same way refreshed regions work today; an error answer swaps nothing, so the dots keep their last state.
- Dots carry an accessible label and a title with the part's name and reason; state is shown by shape or icon as well as colour.
- CLI: new `status` subcommand; `--strict` makes warnings fail. Exit 0 / 1, and the usual exit when the dashboard does not answer.
- The Route Profiles spec adds a fifth concern later (a broken `route-profiles.yaml`) to the Traefik part.

## Testing Decisions

- A good test checks only what a user could see: API answers, page and fragment HTML, CLI output and exit codes.
- Seam 1, the dashboard, with real Consul and Docker from Testcontainers, and a real Traefik (as in the Reachability tests) for ping and last fetch. Tests check each part's OK state; Traefik failing when stopped; Consul's WAN pool with the two-datacenter Yard; a missing peer; a missing Core Service; and that the summary fragment and page render each state.
- The Docker failing state is tested with a dashboard pointed at a socket path that does not exist.
- Seam 2, the CLI, with the stub dashboard: output for each state, exit codes, `--strict`.
- The OpenAPI test covers `/api/status`.
- Prior art: the catalog page test (refreshed regions), the reachability test (real Traefik), and the CLI test.

## Out of Scope

- Status of other Workstations beyond their WAN membership.
- History or alerts.
- Docker's own container HEALTHCHECK states (shown by the Docker import page instead).
