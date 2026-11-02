# LVH Yard

Reach any development Service on any Workstation in your Yard through a
predictable hostname, such as `http://grafana.lvh.me`, without knowing its IP
address or port. The words are defined in [GLOSSARY.md](GLOSSARY.md), the
decisions in [docs/adr/](docs/adr/), and the MVP in
[.scratch/mvp/spec.md](.scratch/mvp/spec.md).

Every Workstation runs its own Consul datacenter, federated with the others
([ADR-0001](docs/adr/0001-one-consul-datacenter-per-workstation.md)), a Traefik
on `127.0.0.1:80`, and the dashboard, which gives Traefik its Routes
([ADR-0002](docs/adr/0002-traefik-reads-routes-from-lvh-yard.md)).

## Requirements

- JDK 25 (the build selects it through a Maven toolchain)
- Docker with Compose, for Consul and Traefik
- [mise](https://mise.jdx.dev/), for the tasks and Node (Tailwind CSS)

## Running on a Workstation

1. Copy `.env.example` to `.env`, and set `YARD_WORKSTATION`, `YARD_GOSSIP_KEY`
   and `YARD_PEERS`.
2. `mise run up` starts Consul and Traefik.
3. `mise run dashboard` starts the dashboard. Open
   [yard.lvh.me](http://yard.lvh.me), or `127.0.0.1:8780` when Traefik is down.

The other Workstations must reach this one's Consul server and serf WAN ports
(8300, and 8302 over TCP and UDP).

### The dashboard from its image

A Workstation without Java can run the dashboard from its image,
`ghcr.io/ppalazon/lvh-yard-dashboard`, next to Consul and Traefik. Add
`COMPOSE_PROFILES=dashboard` to `.env`, then `docker compose up -d` starts all
three. Set `YARD_DASHBOARD_IMAGE` to pin a version, such as
`ghcr.io/ppalazon/lvh-yard-dashboard:0.1.0`. `docker compose build dashboard`
builds the image from this repository instead.

The dashboard uses host networking and this Workstation's Docker socket. On
Docker Desktop, turn on host networking in its settings.

## A Yard on one computer

Two test Workstations, ws-a and ws-b, can run side by side on loopback
(`dev/*.env`):

```sh
mise run dev:up                 # Consul and Traefik for both
mise run dev:dashboard ws-a     # http://yard.lvh.me
mise run dev:dashboard ws-b     # http://yard.lvh.me:8081
mise run dev:down
```

## The CLI

Each release has `yard` as one executable for Linux (amd64, arm64) and macOS
(arm64), with no Java needed. Download it from the
[releases](https://github.com/ppalazon/lvh-yard/releases), check it against
`SHA256SUMS`, and put it on your `PATH`:

```sh
sha256sum -c SHA256SUMS --ignore-missing
tar -xzf yard-0.1.0-linux-amd64.tar.gz yard
sudo mv yard /usr/local/bin/
```

On macOS, a downloaded executable is blocked until you clear its quarantine:
`xattr -d com.apple.quarantine yard`.

From the sources, as a jar, or as the executable. `mise run native` installs
GraalVM for that task only, builds `cli/target/yard`, and runs the CLI's tests
again against it:

```sh
./mvnw -q -pl cli package
java -jar cli/target/cli-0.0.1-SNAPSHOT.jar --help
mise run native
```

`yard` talks to the dashboard on this Workstation, at `--dashboard`, else
`YARD_DASHBOARD_URL`, else `http://127.0.0.1:8780`. It sees the whole Yard, but
changes only this Workstation's Services:

```sh
yard list                               # every Service by Workstation, this one first: state, hostname, Instances, Health
yard list --workstation ws-b            # one Workstation's
yard register web --port 8080           # at this Workstation's LAN address
yard register web --host 127.0.0.1 --port 8080
yard register web --port 8080 --path /health --interval 5s --timeout 1s   # an HTTP check (default: TCP, 10s, 2s)
yard register web --port 8080 --tag backend --meta owner=pablo           # repeat --tag and --meta for more
yard register web --container web-1 --port 80                            # follow the container web-1, port 80 inside it
yard register web --compose-project shop --compose-service web --port 80 # follow every container of Compose service web
yard remove web --port 8080             # add --host when the port is used on more than one host
yard remove web --id <id>               # the ID that yard list shows
yard remove web --container web-1       # add --port when web-1 is bound on more than one port
yard remove web --compose-project shop --compose-service web
yard publish web                        # http://web.<base-domain> on this Workstation (Local Scope)
yard publish web --scope yard           # also http://web.<this-workstation>.<base-domain> on every other one
yard unpublish web                      # removes the hostname; every Instance stays registered
yard alias add grafana --workstation ws-b                 # http://grafana.<base-domain> for grafana of ws-b, here only
yard alias add db --workstation ws-b --service postgres   # another name than the Service's
yard alias list                         # each Alias, its Remote Service and its state
yard alias remove grafana
yard test web                           # can this Workstation reach web: each Instance, and each hostname through Traefik
yard test grafana --workstation ws-b    # a Remote Service
```

Each Service is `unpublished`, `unavailable` (published, no Instances),
`unhealthy` (published, no passing Instance) or `healthy` (published, with a
passing Instance). A Publication is kept in this Workstation's Consul KV, under
`lvh-yard/publications/`. Traefik routes it to every Instance of the Service,
including ones registered later.

A new Publication has Local Scope: only this Workstation routes it. With Yard
Scope, every other Workstation routes it too, at
`<service>.<workstation>.<base-domain>`; this Workstation keeps
`<service>.<base-domain>`. Publishing again without `--scope` keeps the Scope.
A Yard-scoped Service warns, on its page, in the API and in `yard publish`,
about each Instance on a loopback address, which other Workstations cannot
reach. An Instance of a Remote Service on a loopback address or a Docker bridge
network (172.17.0.0/16 to 172.31.0.0/16) is out of range here: the API
(`outOfRange`), the page and `yard list` mark it, and no Route points to it,
because it would reach this computer's own loopback or containers.
`YARD_OUT_OF_RANGE` replaces that list when your LAN uses those addresses; the
test Yard leaves loopback out, as its two Workstations share it. The Routes of
a Workstation that stops answering, or whose Services fail to read for any
other reason, are dropped, and come back once it is read again; the other
Workstations keep theirs.

A Reachability test answers "can I reach it from here?". Press Test on a
Service's page, run `yard test`, or GET
`/api/services/<workstation>/<service>/reachability`. It connects straight to
each Instance from this Workstation, and requests each hostname this
Workstation routes to the Service (its own, and its active Aliases') through
the local Traefik, at `YARD_HTTP_PORT` on loopback. A Service with no Route
here is tested straight to its Instances only, and the result says why it has
no Route. An Out-of-range Instance is skipped (`outOfRange` in the API, `skip`
in `yard test`), as a connection would reach this computer instead; a skipped
Instance is no failure. A request through Traefik fails when Traefik has no
Route for the hostname, or answers 502, 503 or 504 because no Instance answered
it; any other answer means it reached the Service. The results are shown, never
stored, and `yard test` exits with 1 when any check fails.

An Alias gives a Remote Service a short hostname on this Workstation only, such
as `grafana.<base-domain>` for `grafana.ws-b.<base-domain>`. Add, list and
remove them on the Aliases page or with `yard alias`. An Alias is kept in this
Workstation's Consul KV, under `lvh-yard/aliases/`, and no other Workstation
routes it. Its name follows the Service name rules, and `yard` is refused. It
must name a Service of another Workstation of the Yard. An Alias is `active`,
routed to the Remote Service's Instances, while that Service has Yard Scope.
Otherwise it has no Route and says why: `unavailable` (the Service has no
Instance in range here), `hidden` (a published Service of this Workstation has
the same name, and wins), `not-shared` (the Service is unpublished or has Local
Scope), `unreachable` (its Workstation does not answer) or `missing` (the
Service or its Workstation is gone).

A Container Binding follows a Docker container by its name, or every container
of a Compose project and service, and a container port, so the Service follows
them wherever their address and port go. Add one on the Register page or with
`yard register --container` (or `--compose-project` and `--compose-service`),
and remove it on the Service's page or with `yard remove` and the same options.
It is kept in this Workstation's Consul KV, under
`lvh-yard/container-bindings/`. A Compose service's containers are found by the
labels Compose gives them, whatever they are named, so a scaled service gives
one Bound Instance per container, and scaling it up or down adds or removes
them. While a matching container runs, the Service has a Bound Instance for it:
at this Workstation's LAN address and the host port when the container port is
published on every host address, else at the container's own address and the
container port. It gets a TCP check, as a Static Instance with no check does,
and it cannot be edited or removed by itself. The dashboard follows Docker's
events at `DOCKER_HOST`, else `unix:///var/run/docker.sock`. When it starts, or
when Docker answers again, it brings every Container Binding in step with the
containers running then. A Binding whose containers do not run shows on the
Service's page and in the API with no Bound Instance.

Each Instance has one health check: TCP, which connects to its port, or HTTP,
which GETs a path and passes on a 2xx answer. Script checks are not offered.
The Service's page shows each check's last output, and edits an Instance's
host, port, check, tags and meta; it stays the same Instance.

A Remote Service, one whose Home Workstation is another, can be seen but not
changed: its page has no forms, and the API refuses any change asked under
`/api/services` with 405. A Workstation whose Consul datacenter does not answer
shows as "Workstation unreachable", and its Services show again by themselves
once it answers.

Every failure prints its reason and exits with a non-zero code.

## Checks

`mise run check` builds the stylesheet, builds both modules from scratch and
runs every test. The dashboard's tests start a Yard of two federated Consul
datacenters with Testcontainers, so they need Docker. The second Workstation,
`ws-remote`, runs a dashboard of its own in the same JVM.

`dashboard/openapi.yaml` describes the REST API that the CLI uses. A test fails
when it differs from what the dashboard serves; after changing the API,
`mise run openapi` rewrites it.

## Releasing

Run `mise run check`, then push a tag:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The Release workflow (`.github/workflows/release.yml`) builds the dashboard
image for amd64 and arm64, with the tag as its version, and pushes it to
`ghcr.io/ppalazon/lvh-yard-dashboard` as `0.1.0`, `0.1` and `latest`. After the
first release, make the package public once in its settings on GitHub.

It also builds `yard` for each platform, runs the CLI's tests against it, and
attaches the executables and their `SHA256SUMS` to a GitHub release, whose notes
list the commits since the last tag. The release appears only when the image and
every executable are ready.

## AI-assisted development

This project was developed with assistance from AI coding tools, including
Anthropic Claude.

The project maintainer is responsible for its architecture, implementation
decisions, code review, testing, and maintenance.

AI-generated contributions are reviewed before inclusion in the project.

## License

LVH Yard is licensed under the Apache License, Version 2.0. See the
[LICENSE](LICENSE) file for details.
