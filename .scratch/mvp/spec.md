# LVH Yard MVP

Status: ready-for-agent

Words follow `GLOSSARY.md`. Decisions follow ADR-0001 (one Consul datacenter per Workstation), ADR-0002 (Traefik reads its Routes from LVH Yard) and ADR-0003 (hostnames of Remote Services name their Home Workstation).

## Problem Statement

I develop on more than one computer, and so do my teammates. Our development services (Docker containers, Compose stacks, systemd services, Vite dev servers, Spring Boot apps, databases) run on whichever computer is handy. To reach one, I have to remember which computer it is on, that computer's IP address, and a port that is often random or changes on every restart. When a teammate wants to try my service, I have to tell them all of that, and it is out of date by the next day. Nothing tells me whether a service is down, not running, or simply not reachable from where I sit.

## Solution

Every computer I develop on becomes a Workstation in a Yard. On each Workstation I register my Services once: as a Static Instance at a fixed address, or as a Container Binding that follows a Docker container wherever its address and port go. When I publish a Service, it gets a predictable hostname: `<service>.lvh.me` on my own Workstation, and `<service>.<my-workstation>.lvh.me` on every other Workstation, if I choose Yard Scope. A dashboard on each Workstation, at `yard.lvh.me`, shows every Service in the Yard, which Workstation it is on, whether it is published, whether its Instances are healthy, and whether I can actually reach it from where I am. A `yard` command does the same from a terminal. LVH Yard never starts or stops my processes: it only gives them names.

## User Stories

### Setting up a Workstation

1. As a developer, I want to start a Workstation's Consul and Traefik with one command, so that my computer joins the Yard without manual setup.
2. As a developer, I want to set my Workstation's name, the base domain, the gossip key and my peers in one settings file, so that every part of the Workstation reads the same settings.
3. As a developer, I want my Workstation to keep working on its own when every other Workstation is off, so that a teammate's computer being off never breaks my Routes.
4. As a developer, I want my Workstation to join the other Workstations listed as peers whenever they come up, so that the Yard forms by itself.
5. As a developer, I want a stray Consul on the team LAN without the Yard's gossip key to be unable to join, so that nobody joins the Yard by mistake.
6. As a developer, I want Consul's management API and Traefik to listen on loopback only, so that nothing on the LAN can change my registrations or use my proxy.
7. As a developer, I want to open the dashboard at `yard.<base-domain>`, so that I do not have to remember its port.
8. As a developer, I want to open the dashboard at its loopback port when Traefik is down, so that I can still see and fix things.
9. As a developer, I want to change the base domain from `lvh.me` to `localhost`, so that the Yard keeps working offline or behind a router that blocks `lvh.me`.
10. As a developer, I want to try a Yard of two Workstations on my one computer, so that I can see federation work before I have a second computer.

### Registering Static Instances

11. As a developer, I want to register a Static Instance of a Service by giving a name, a host and a port, so that a service LVH Yard did not launch becomes known to the Yard.
12. As a developer, I want the host to default to my Workstation's LAN address, so that other Workstations can reach the Instance without extra typing.
13. As a developer, I want to register more than one Static Instance of the same Service, so that Traefik can spread requests across interchangeable copies.
14. As a developer, I want a Service name to be refused unless it is a single DNS label, so that every Service has a valid hostname.
15. As a developer, I want the name `yard` to be refused for a Service, so that nothing hides the dashboard.
16. As a developer, I want a Static Instance to stay registered when its process stops, so that the Yard remembers it and shows it as unhealthy instead of forgetting it.
17. As a developer, I want my Static Instances to survive a restart of my Workstation's Consul, so that I register them only once.
18. As a developer, I want to remove a Static Instance, so that a service I no longer run disappears from the Yard.
19. As a developer, I want to edit a Static Instance's host, port and health check, so that I can fix a mistake without removing and adding it again.
20. As a developer, I want to add tags and metadata to a Static Instance, so that I can record what it is for.

### Container Bindings

21. As a developer, I want to bind a Service to a Docker container by its name, so that the Service follows the container even when its address and port change.
22. As a developer, I want to bind a Service to a Compose project and service, so that a Compose service is bound however its containers are named.
23. As a developer, I want a Container Binding to name the container port, so that LVH Yard finds whichever host port Docker mapped to it.
24. As a developer, I want a Bound Instance to appear when a matching container starts, so that the Service becomes available without any action from me.
25. As a developer, I want a Bound Instance to disappear when its container stops, so that a published Service shows as Unavailable instead of pointing at nothing.
26. As a developer, I want a scaled Compose service to give one Bound Instance per container, so that Traefik spreads requests across all of them.
27. As a developer, I want a Bound Instance to use the LAN address and the published host port when the container port is published, so that other Workstations can reach it.
28. As a developer, I want a Bound Instance to fall back to the container's own address and port when the port is not published, so that I can still route to it from my own Workstation.
29. As a developer, I want LVH Yard to catch up with containers that changed while it was not running, so that Bound Instances are never stale after a restart.
30. As a developer, I want to remove a Container Binding, so that LVH Yard stops following that container.
31. As a developer, I want to see a Container Binding that currently matches no container, so that I know the Binding exists even while its Service is Unavailable.

### Health

32. As a developer, I want to give an Instance an HTTP health check with a path, an interval and a timeout, so that Health reflects whether the application actually answers.
33. As a developer, I want to give an Instance a TCP health check, so that services that do not speak HTTP still get a Health.
34. As a developer, I want an Instance with no health check to get a TCP check on its port, so that every Instance has a Health.
35. As a developer, I want to read each check's last output, so that I can see why an Instance is critical.
36. As a developer, I want health checks to run on the Service's Home Workstation, so that a check never depends on another Workstation being on.

### Publications and Scope

37. As a developer, I want to publish a Service, so that it gets a hostname.
38. As a developer, I want a new Publication to have Local Scope, so that sharing with my team is always a deliberate step.
39. As a developer, I want to change a Publication's Scope to Yard, so that every teammate's Workstation gets a hostname for my Service.
40. As a developer, I want to unpublish a Service without touching its Instances or its process, so that hiding a hostname never stops my work.
41. As a developer, I want my published Services to answer at `<service>.<base-domain>` on my own Workstation, so that their hostnames are short.
42. As a developer, I want a teammate's Yard-scoped Services to answer at `<service>.<workstation>.<base-domain>` on my Workstation, so that their `api` and my `api` never clash.
43. As a developer, I want a Publication to cover every Instance of the Service, including ones that appear later, so that a new container is routed without republishing.
44. As a developer, I want a warning when a Yard-scoped Service has an Instance on a loopback address, so that I notice teammates cannot reach it.
45. As a developer, I want a warning when a Yard-scoped Service has a Bound Instance whose container port is not published, so that I notice teammates cannot reach it.
46. As a developer, I want Traefik to keep my Routes while the dashboard restarts, so that a dashboard restart does not take my hostnames down.

### Remote Services

47. As a developer, I want to see every Service in the Yard, grouped by Home Workstation, so that I know what my teammates run.
48. As a developer, I want a Remote Service's pages to be read-only, so that I never change a teammate's Services by mistake.
49. As a developer, I want a Workstation that cannot be reached to be shown as "Workstation unreachable", so that I know why its Services are missing.
50. As a developer, I want the Routes to an unreachable Workstation's Services to be dropped, so that Traefik does not send me to a computer that is off.
51. As a developer, I want a Remote Service's Routes to come back by themselves when its Workstation comes back, so that I never have to refresh anything.

### Aliases

52. As a developer, I want to give a Remote Service a short Alias on my Workstation, such as `grafana.lvh.me` for `grafana.ws-b.lvh.me`, so that a Service I use every day has a short hostname.
53. As a developer, I want my Aliases to exist only on my Workstation, so that they never change what my teammates see.
54. As a developer, I want my own Service to win over an Alias with the same name, and the Alias to be flagged as hidden, so that adding a Service never silently breaks it.
55. As a developer, I want to list and remove my Aliases, so that I can clean them up.

### Service states and Reachability

56. As a developer, I want each Service to show whether it is Unpublished, Unavailable, Unhealthy, or published and healthy, so that I can tell "I hid it" from "it is not running" from "it is broken".
57. As a developer, I want to test Reachability of a Service from the Workstation I am using, straight to each Instance and through its Route, so that I can tell a firewall or a loopback address from a broken application.
58. As a developer, I want Reachability results to be shown but not stored, so that I never read a stale result.

### Dashboard

59. As a developer, I want a Catalog page listing every Service in the Yard with its state and hostnames, so that I see the whole Yard at a glance.
60. As a developer, I want to filter the Catalog by Workstation and by state, so that I find a Service quickly.
61. As a developer, I want a Service detail page with its Instances, each check's Health output, its Publication and Scope, its hostnames, its Container Bindings and a Reachability test, so that I can understand and fix one Service in one place.
62. As a developer, I want one Register form for both Static Instances and Container Bindings, so that adding a Service is one step.
63. As a developer, I want an Aliases page, so that I manage my Aliases in one place.
64. As a developer, I want the pages to refresh themselves every few seconds, so that I see containers start and stop without reloading.

### CLI

65. As a developer, I want `yard list` to show every Service in the Yard with its state and hostnames, so that I can check the Yard from a terminal.
66. As a developer, I want `yard register` to add a Static Instance or a Container Binding, so that I can script my setup.
67. As a developer, I want `yard remove` to remove a Static Instance or a Container Binding, so that I can script my teardown.
68. As a developer, I want `yard publish` with an optional Scope and `yard unpublish`, so that I can share and hide Services from a terminal.
69. As a developer, I want `yard alias` to add, list and remove Aliases, so that I manage them from a terminal.
70. As a developer, I want `yard test` to run a Reachability test, so that I can debug a connection from a terminal.
71. As a developer, I want the CLI to start fast and to exit with a non-zero code on failure, so that it works in scripts.
72. As a developer, I want the CLI to explain clearly when the dashboard is not running, so that I know what to start.

## Implementation Decisions

### What exists

- The repository is one Maven multi-module build with a `dashboard` module and a `cli` module, plus the Workstation stack (Consul and Traefik) in a compose file and `mise` tasks. The stack and the build already work. Two test Workstations can run side by side on loopback, and their federation and the `yard` Route through Traefik have been checked by hand.
- The dashboard already serves an empty Catalog page and Traefik's configuration, which routes only `yard.<base-domain>` to the dashboard.

### Architecture

- Every Workstation runs Consul as its own datacenter, named after the Workstation, joined to its peers by WAN federation (ADR-0001). The Consul HTTP API listens on loopback only. The dashboard asks its local Consul about other datacenters, and Consul forwards those questions.
- Every Workstation runs Traefik on loopback, reading its Routes from the local dashboard through its HTTP provider (ADR-0002). When the dashboard does not answer, Traefik keeps its last Routes.
- Consul is the only store. Static Instances and Bound Instances are Consul service registrations on the Home Workstation's agent. Publications and Container Bindings are kept in the Home Workstation's Consul KV. Aliases are kept in the local Consul KV. The dashboard has no database.
- Consul and Traefik run with host networking. The only ports facing the LAN are Consul's server and serf WAN ports.

### Dashboard modules

- **Yard reader**: reads every datacenter in the Yard (Instances, their Health and check output, Publications, Container Bindings), and marks a datacenter that does not answer as "Workstation unreachable". It is the only reader of other datacenters.
- **Registrations**: adds, edits and removes Static Instances on the local agent. It validates Service names (a single DNS label, not `yard`) and adds a TCP check when none is given. It is the only writer of local service registrations.
- **Container watcher**: keeps Bound Instances in step with Docker. It follows Docker events, re-syncs every Container Binding on startup, and works out each Bound Instance's address (the LAN address and the published host port, or else the container's address and container port).
- **Publications and Aliases**: reads and writes Publications (with Scope) in local KV, and Aliases in local KV. Writing is only allowed for this Workstation's own Services.
- **Route builder**: turns what the Yard reader returns, plus the local Aliases, into Traefik's configuration. Own Services get `<service>.<base-domain>`. Remote Services with Yard Scope get `<service>.<workstation>.<base-domain>`. Aliases get `<alias>.<base-domain>` unless an own Service has that name. Unreachable Workstations get no Routes. The dashboard always gets `yard.<base-domain>`. This is a pure function of its input and holds all the hostname rules.
- **Service states**: works out Unpublished, Unavailable, Unhealthy or published and healthy, and the two Scope warnings (loopback address, unpublished container port).
- **Reachability**: on request, connects from this Workstation to each Instance, and through the local Traefik using the Route's hostname. It returns the results without storing them.
- **Pages**: Catalog, Service detail, Register and Aliases, server-rendered and refreshed by HTMX polling. A Remote Service's pages have no forms.
- **REST API**: the same operations as the pages, as JSON. It is described by a committed `openapi.yaml` and is the only thing the CLI uses.

### API contract

- Resources: the Yard's Services (list, filterable by Workstation and state), one Service (detail), a Service's Static Instances, a Service's Container Bindings, a Service's Publication (with Scope), this Workstation's Aliases, and a Reachability test of one Service.
- A Remote Service is addressed by Workstation and Service name. Changing a Remote Service is refused.
- Invalid names, the reserved name `yard`, and edits to Remote Services are refused with a client error that explains why.

### CLI

- The `yard` command talks only to the local dashboard's REST API, at a URL taken from an option or an environment variable with a loopback default. It never talks to Consul.
- Commands: `list`, `register`, `remove`, `publish`, `unpublish`, `alias` and `test`.
- It uses picocli and the JDK HTTP client, with no Spring. It is packaged as one runnable jar.

### Choices made in this spec

These were not discussed in the design session. They are chosen here, and are easy to change:

- A Service exists on its Home Workstation while it has at least one Static Instance, Container Binding or Publication. Removing the last one removes the Service.
- The default health check interval is 10 seconds, and the default timeout is 2 seconds.
- The pages refresh every 5 seconds, and Traefik polls the dashboard every 5 seconds.
- Consul's KV keys live under one LVH Yard prefix, per datacenter.

## Testing Decisions

- A good test drives the system only through its outside surface, and checks only what a user could see: HTTP responses, pages, Traefik's configuration, CLI output and exit codes. It never reaches into internal classes or Consul keys.
- **Seam 1, the dashboard**: tests start the dashboard and talk to it over HTTP, extending the existing dashboard test base. Behind it, tests use real infrastructure started by Testcontainers:
  - real Consul, with two federated datacenters for any test that needs a Yard;
  - real Docker, where a test starts small containers to exercise Container Bindings;
  - a real Traefik, only for the Reachability tests and one end-to-end Route test.
- The Route builder's hostname rules are checked through the Traefik configuration that the dashboard serves, not by calling the builder directly.
- **Seam 2, the CLI**: tests run the `yard` command with arguments, and check its output and exit code, extending the existing CLI test. A WireMock stub dashboard answers only what `openapi.yaml` describes.
- **The contract**: an OpenAPI test fails when the committed `openapi.yaml` differs from what the running dashboard serves.
- Prior art: the existing dashboard and CLI tests in this repository, and `astro-alert`'s black-box test base, health test and OpenAPI test.

## Out of Scope

- HTTPS and a local CA. This is the next phase. It needs one wildcard certificate for the base domain, plus one per Workstation for its nested hostnames.
- TCP and UDP routing, a pool of reserved TCP ports, and Postgres routing by hostname (SNI).
- Automatic Docker discovery through container labels.
- systemd integration.
- Alerts and notifications.
- Publishing Services to the Internet.
- Starting, stopping or supervising processes.
- Logins, accounts and permissions, beyond "a Workstation changes only its own Services".
- Script health checks.
- Kubernetes and any other orchestration platform.
- A database of LVH Yard's own.
- Devices that are not Workstations, such as phones.
- Finding peers automatically. Peers are listed by hand.

## Further Notes

- **`lvh.me` needs public DNS.** It fails offline and behind routers with DNS rebinding protection. The base domain `localhost` is the fallback.
- **Firewalls.** Teammates' Workstations must allow Consul's server and serf WAN ports between them.
- **Polling cost.** The dashboard and Traefik ask every few seconds, across every datacenter. If this gets slow, Consul blocking queries are the fix.
- **Pinned versions.** The Workstation stack pins exact Consul (2.1.x) and Traefik (3.7.x) versions. Consul 2.x needed no configuration changes from 1.x.
- **Test Yard.** The test Yard on one computer uses a committed gossip key, which is safe only because it listens on loopback.
