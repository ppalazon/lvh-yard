# Development

This page is for people who work on LVH Yard itself. To install and use LVH
Yard, read the [user guide](user-guide.md). [.scratch/mvp/spec.md](../.scratch/mvp/spec.md)
describes the MVP.

## Requirements

You need Docker with Compose, JDK 25 and [mise](https://mise.jdx.dev/). The
build selects the JDK through a Maven toolchain. mise runs the tasks and
installs Node for Tailwind CSS.

## Running from the sources

1. Copy `.env.example` to `.env`. Set `YARD_WORKSTATION`, `YARD_GOSSIP_KEY`
   and `YARD_PEERS`.
2. Run `mise run up` to start Consul and Traefik.
3. Run `mise run dashboard:run` to start the dashboard from the sources.

To run the dashboard image instead, add `COMPOSE_PROFILES=dashboard` and
`YARD_DOCKER_GID` to `.env`. Then run `docker compose up -d`. To pin a version,
set `YARD_DASHBOARD_IMAGE`, for example to
`ghcr.io/ppalazon/lvh-yard-dashboard:0.1.0`. `docker compose build dashboard`
builds the image from this repository.

To try the image from your sources on another Workstation, build it and copy it
there through ssh. The other Workstation needs Docker and ssh, but no registry.

```sh
mise run dashboard:image                        # for the CPU of this computer
mise run dashboard:image --platform linux/arm64 # for a Workstation with another CPU
mise run dashboard:copy user@host
```

Both tasks use the tag `ghcr.io/ppalazon/lvh-yard-dashboard:latest`. It is the
default image of `compose.yaml`. On the other Workstation, unset
`YARD_DASHBOARD_IMAGE` in `.env` and run `docker compose up -d`.

## A Yard on one computer

Two test Workstations, ws-a and ws-b, can run side by side on loopback. Their
settings are in `dev/ws-a.env` and `dev/ws-b.env`. Each one is a full `.env`,
and ws-b uses the Base Domain `localhost`.

```sh
mise run dev:up                 # Consul and Traefik for both
mise run dev:dashboard ws-a     # http://yard.lvh.me
mise run dev:dashboard ws-b     # http://yard.localhost:8081
mise run dev:down
```

Each one reads its Route Profiles from its own file,
`dev/ws-a.route-profiles.yaml` or `dev/ws-b.route-profiles.yaml`. Git ignores
these files.

## Building the CLI

To build the CLI as a jar or as the executable:

```sh
./mvnw -q -pl cli package
java -jar cli/target/cli-0.0.1-SNAPSHOT.jar --help
mise run native                 # builds cli/target/lvh-yard with GraalVM and tests it
```

## Tests

`mise run check` builds the stylesheet, builds both modules from scratch and
runs every test. The dashboard tests start a Yard of two federated Consul
datacenters with Testcontainers, so they need Docker. The second Workstation,
`ws-remote`, runs its own dashboard in the same JVM. The test classes run in
four JVMs at the same time, and each JVM starts its own test Yard.

`dashboard/openapi.yaml` describes the REST API that the CLI uses. If it
differs from the API that the dashboard serves, a test fails. After you change
the API, run `mise run openapi` to write it again.

## Releasing

Run `mise run check`, then push a tag:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The Release workflow (`.github/workflows/release.yml`) then does these steps:

1. It builds the dashboard image for amd64 and arm64, with the tag as its
   version.
2. It pushes the image to `ghcr.io/ppalazon/lvh-yard-dashboard` as `0.1.0`,
   `0.1` and `latest`.
3. It builds `lvh-yard` for each platform and runs the CLI tests against it.
4. It attaches the executables and their `SHA256SUMS` to a GitHub release. The
   release notes list the commits since the last tag.

When the image and every executable are ready, the release appears. After the
first release, make the package public once in its settings on GitHub.
