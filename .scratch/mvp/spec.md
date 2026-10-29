# LVH Yard MVP

Status: needs-triage

## Goal

Reach any development Service on any Workstation in the Yard through a predictable hostname, without knowing its IP address or port. LVH Yard manages Services, not the processes behind them.

Words in this spec follow `GLOSSARY.md`.

## Non-goals

- Starting, stopping or supervising processes.
- Logins, accounts and permissions. The Yard is a trusted team LAN: every Workstation sees every Yard-scoped Service, and a Workstation changes only its own Services.
- Kubernetes or any other orchestration platform.
- A database of LVH Yard's own. Consul is the only store ([ADR-0002](../../docs/adr/0002-traefik-reads-routes-from-lvh-yard.md)).
- Script health checks.
- Devices that are not Workstations, such as phones.

## Architecture

Every Workstation runs:

- **Consul** as its own datacenter, named after the Workstation, joined to the other Workstations by WAN federation ([ADR-0001](../../docs/adr/0001-one-consul-datacenter-per-workstation.md)). Its HTTP API listens on `127.0.0.1` only. Only the federation ports (8300, and 8302 TCP and UDP) face the LAN.
- **Traefik** on `127.0.0.1:80`, reading its routes from the local dashboard through its HTTP provider.
- **The dashboard** (`dashboard` module): the web UI, the REST API, the Traefik route endpoint and the Docker watcher.

Consul and Traefik run from a compose file with host networking. Each Workstation's `.env` gives its Workstation name, the base domain, the other Workstations' LAN IPs and the Yard's shared gossip key.

The `cli` module talks only to the local dashboard's REST API. The two modules share no code: `openapi.yaml` is their contract.

## Hostnames

- The base domain is one setting for the whole Yard. It defaults to `lvh.me`.
- A Workstation's own Services: `<service>.<base-domain>`.
- Remote Services: `<service>.<workstation>.<base-domain>` ([ADR-0003](../../docs/adr/0003-hostnames-name-the-home-workstation.md)).
- An Alias gives a Remote Service a short `<alias>.<base-domain>` on one Workstation. A Service of that Workstation with the same name wins, and the dashboard flags the Alias as hidden.
- Service names and Workstation names are single DNS labels: lowercase letters, digits and `-`. `yard` is reserved: the dashboard publishes itself at `yard.<base-domain>` and also answers on `127.0.0.1:<port>`.
- Different environments or data are different Services (`api-dev`, `api-test`). Instances of one Service are interchangeable.

## Instances

### Static Instances

- You give a host and port. The host defaults to the Home Workstation's LAN IP. `127.0.0.1` is allowed, with a warning when the Service's Scope is Yard.
- A Static Instance stays registered until you remove it. When its process stops, its Health turns critical.

### Container Bindings

- A Container Binding names a container (plain `docker run --name`) or a Compose project and service, plus a container port.
- Every running matching container gives the Service a Bound Instance. A scaled Compose service gives one Bound Instance per container.
- Address: the LAN IP and the published host port when the container port is published. Otherwise the container IP and the container port, which only the Home Workstation can reach, so the dashboard warns when the Scope is Yard.
- The dashboard watches Docker events and registers or removes Bound Instances as containers start and stop. It re-syncs every Binding on startup.
- Container Bindings are stored in the Home Workstation's Consul KV.

## Health and Reachability

- Health checks are HTTP or TCP, with an interval and a timeout, and they run on the Home Workstation's Consul agent. An Instance with no check gets a TCP check on its port.
- Reachability is a test you start from the dashboard or the CLI. It runs from the Workstation you are using, straight to each Instance and through the Route, and shows the result without storing it.

## Publications

- A Publication is opt-in. It has a Scope: Local (only the Home Workstation gets a Route) or Yard (every Workstation does). New Publications default to Local.
- Publishing never starts or stops a process. Unpublishing never unregisters an Instance.
- Publications are stored in the Home Workstation's Consul KV. Aliases are stored in the local Consul KV.
- The dashboard builds Traefik's routes from every datacenter's Instances and Health, the Publications and the local Aliases. When another Workstation's datacenter is unreachable, its Routes are dropped and its Services show as "Workstation unreachable".
- The dashboard shows each Service's state: Unpublished, Unavailable, Unhealthy, or published and healthy.

## Dashboard pages

All pages refresh themselves every few seconds through HTMX. A Remote Service's pages are read-only.

- **Catalog**: every Service in the Yard, grouped by Workstation, with its state and hostnames. You can filter by Workstation and by state.
- **Service detail**: Instances, each check's Health output, the Publication and its Scope, hostnames, Container Bindings, and a Reachability test.
- **Register**: a form to add a Static Instance or a Container Binding.
- **Aliases**: this Workstation's Aliases.

## CLI

The MVP commands are `list`, `register`, `remove`, `publish`, `unpublish`, `alias` and `test`.

## Stack

- `dashboard`: Java 25, Spring Boot 4.1, Spring MVC, HtmlFlow, HTMX, Tailwind 4 with Basecoat, Spring's `RestClient` for Consul, and `docker-java` for Docker. `mise` tasks, as in `astro-alert`.
- `cli`: Java 25, picocli and the JDK `HttpClient`, with no Spring.
- The compose file pins exact Consul and Traefik versions.
- One Maven multi-module repo.

## Later, in this order

1. HTTPS with a local CA. It needs one wildcard certificate for `*.<base-domain>` and one per Workstation for `*.<workstation>.<base-domain>`.
2. TCP routing through a pool of Traefik entrypoints reserved in advance.
3. Automatic Docker discovery through container labels.
4. systemd integration.
5. Alerts and notifications.
6. UDP, and Postgres routing by hostname (SNI).
7. Publishing selected Services to the Internet.

## Risks

- **Testing a Yard with one computer** needs two Consul datacenters side by side, for example a second compose stack on other ports.
- **`lvh.me` needs public DNS.** It fails offline, and behind routers with DNS rebinding protection. The fallback is the base domain `localhost`.
- **Firewalls.** Coworkers' Workstations must allow ports 8300 and 8302 between them.
- **Polling cost.** The dashboard asks every datacenter every few seconds. Consul blocking queries are the fix if this gets slow.
