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

`yard` talks to the dashboard on this Workstation, at `--dashboard`, else `YARD_DASHBOARD_URL`, else `http://127.0.0.1:8780`. It changes only this Workstation's Services:

```sh
yard list                               # this Workstation's Services, Instances and Health
yard list --workstation ws-b            # another Workstation's
yard register web --port 8080           # at this Workstation's LAN address
yard register web --host 127.0.0.1 --port 8080
yard remove web --port 8080             # add --host when the port is used on more than one host
yard remove web --id <id>               # the ID that yard list shows
```

Every failure prints its reason and exits with a non-zero code.

## Checks

`mise run check` builds the stylesheet, builds both modules from scratch and runs every test. The dashboard's tests start a real Consul with Testcontainers, so they need Docker.

`dashboard/openapi.yaml` describes the REST API that the CLI uses. A test fails when it differs from what the dashboard serves; after changing the API, `mise run openapi` rewrites it.
