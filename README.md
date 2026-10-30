# LVH Yard

Reach any development Service on any Workstation in your Yard through a predictable hostname, such as `http://grafana.lvh.me`, without knowing its IP address or port. The words are defined in [GLOSSARY.md](GLOSSARY.md), the decisions in [docs/adr/](docs/adr/), and the MVP in [.scratch/mvp/spec.md](.scratch/mvp/spec.md).

Every Workstation runs its own Consul datacenter, federated with the others ([ADR-0001](docs/adr/0001-one-consul-datacenter-per-workstation.md)), a Traefik on `127.0.0.1:80`, and the dashboard, which gives Traefik its Routes ([ADR-0002](docs/adr/0002-traefik-reads-routes-from-lvh-yard.md)).

## Requirements

- JDK 25 (the build selects it through a Maven toolchain)
- Docker with Compose, for Consul and Traefik
- [mise](https://mise.jdx.dev/), for the tasks and Node (Tailwind CSS)

## Running on a Workstation

1. Copy `.env.example` to `.env`, and set `YARD_WORKSTATION`, `YARD_GOSSIP_KEY` and `YARD_PEERS`.
2. `mise run up` starts Consul and Traefik.
3. `mise run dashboard` starts the dashboard. Open [yard.lvh.me](http://yard.lvh.me), or `127.0.0.1:8780` when Traefik is down.

The other Workstations must reach this one's Consul server and serf WAN ports (8300, and 8302 over TCP and UDP).

## A Yard on one computer

Two test Workstations, ws-a and ws-b, can run side by side on loopback (`dev/*.env`):

```sh
mise run dev:up                 # Consul and Traefik for both
mise run dev:dashboard ws-a     # http://yard.lvh.me
mise run dev:dashboard ws-b     # http://yard.lvh.me:8081
mise run dev:down
```

## The CLI

```sh
./mvnw -q -pl cli package
java -jar cli/target/cli-0.0.1-SNAPSHOT.jar --help
```

`yard` talks to the dashboard on this Workstation, at `--dashboard`, else `YARD_DASHBOARD_URL`, else `http://127.0.0.1:8780`. It sees the whole Yard, but changes only this Workstation's Services:

```sh
yard list                               # every Service by Workstation, this one first: state, hostname, Instances, Health
yard list --workstation ws-b            # one Workstation's
yard register web --port 8080           # at this Workstation's LAN address
yard register web --host 127.0.0.1 --port 8080
yard register web --port 8080 --path /health --interval 5s --timeout 1s   # an HTTP check (default: TCP, 10s, 2s)
yard register web --port 8080 --tag backend --meta owner=pablo           # repeat --tag and --meta for more
yard remove web --port 8080             # add --host when the port is used on more than one host
yard remove web --id <id>               # the ID that yard list shows
yard publish web                        # http://web.<base-domain> on this Workstation (Local Scope)
yard publish web --scope yard           # also http://web.<this-workstation>.<base-domain> on every other one
yard unpublish web                      # removes the hostname; every Instance stays registered
yard alias add grafana --workstation ws-b                 # http://grafana.<base-domain> for grafana of ws-b, here only
yard alias add db --workstation ws-b --service postgres   # another name than the Service's
yard alias list                         # each Alias, its Remote Service and its state
yard alias remove grafana
```

Each Service is `unpublished`, `unavailable` (published, no Instances), `unhealthy` (published, no passing Instance) or `healthy` (published, with a passing Instance). A Publication is kept in this Workstation's Consul KV, under `lvh-yard/publications/`. Traefik routes it to every Instance of the Service, including ones registered later.

A new Publication has Local Scope: only this Workstation routes it. With Yard Scope, every other Workstation routes it too, at `<service>.<workstation>.<base-domain>`; this Workstation keeps `<service>.<base-domain>`. Publishing again without `--scope` keeps the Scope. A Yard-scoped Service warns, on its page, in the API and in `yard publish`, about each Instance on a loopback address, which other Workstations cannot reach. An Instance of a Remote Service on a loopback address or a Docker bridge network (172.17.0.0/16 to 172.31.0.0/16) is out of range here: the API (`outOfRange`), the page and `yard list` mark it, and no Route points to it, because it would reach this computer's own loopback or containers. `YARD_OUT_OF_RANGE` replaces that list when your LAN uses those addresses; the test Yard leaves loopback out, as its two Workstations share it. The Routes of a Workstation that stops answering, or whose Services fail to read for any other reason, are dropped, and come back once it is read again; the other Workstations keep theirs.

An Alias gives a Remote Service a short hostname on this Workstation only, such as `grafana.<base-domain>` for `grafana.ws-b.<base-domain>`. Add, list and remove them on the Aliases page or with `yard alias`. An Alias is kept in this Workstation's Consul KV, under `lvh-yard/aliases/`, and no other Workstation routes it. Its name follows the Service name rules, and `yard` is refused. It must name a Service of another Workstation of the Yard. An Alias is `active`, routed to the Remote Service's Instances, while that Service has Yard Scope. Otherwise it has no Route and says why: `unavailable` (the Service has no Instance in range here), `hidden` (a published Service of this Workstation has the same name, and wins), `not-shared` (the Service is unpublished or has Local Scope), `unreachable` (its Workstation does not answer) or `missing` (the Service or its Workstation is gone).

Each Instance has one health check: TCP, which connects to its port, or HTTP, which GETs a path and passes on a 2xx answer. Script checks are not offered. The Service's page shows each check's last output, and edits an Instance's host, port, check, tags and meta; it stays the same Instance.

A Remote Service, one whose Home Workstation is another, can be seen but not changed: its page has no forms, and the API refuses any change asked under `/api/services` with 405. A Workstation whose Consul datacenter does not answer shows as "Workstation unreachable", and its Services show again by themselves once it answers.

Every failure prints its reason and exits with a non-zero code.

## Checks

`mise run check` builds the stylesheet, builds both modules from scratch and runs every test. The dashboard's tests start a Yard of two federated Consul datacenters with Testcontainers, so they need Docker. The second Workstation, `ws-remote`, runs a dashboard of its own in the same JVM.

`dashboard/openapi.yaml` describes the REST API that the CLI uses. A test fails when it differs from what the dashboard serves; after changing the API, `mise run openapi` rewrites it.
