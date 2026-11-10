# Development

This page is for people who work on LVH Yard itself. To install and use LVH
Yard, read the [user guide](user-guide.md). [GLOSSARY.md](../GLOSSARY.md)
defines the words, and [docs/adr/](adr/) records the design decisions.

## Requirements

You need Docker with Compose, JDK 25 and [mise](https://mise.jdx.dev/). The
dashboard build selects the JDK through a Maven toolchain. mise runs the tasks
and installs Node for Tailwind CSS and Go for the CLI.

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
mise run dev:dashboard ws-a     # http://yard.lvh.me:10080
mise run dev:dashboard ws-b     # http://yard.localhost:10090
mise run dev:down
```

Each one keeps its Route Profiles in its own Consul KV. To add test profiles,
use the Route Profiles page of its dashboard, or `lvh-yard profiles set`:

```sh
lvh-yard --dashboard http://127.0.0.1:17780 profiles set profiles.yaml   # ws-a
lvh-yard --dashboard http://127.0.0.1:17790 profiles set profiles.yaml   # ws-b
```

Each one writes its Backups to its own folder, `dev/backups/ws-a` or
`dev/backups/ws-b`. Git ignores these folders. The dashboard tests use a new
temporary folder for each test JVM, through the `lvh-yard.backups.path`
setting.

## Building the CLI

The CLI is a Go program in `cli/`. It uses the standard library, Cobra and one
YAML module.

```sh
mise run cli:build      # builds cli/target/lvh-yard
mise run install        # builds it and copies it to ~/.local/bin/lvh-yard
mise run install --dir ~/bin   # copies it to ~/bin/lvh-yard
```

`mise run cli:build` builds without C code and strips debug data. The
executable reports `dev` unless `VERSION` is set at build time. `install`
writes a new file and then moves it, because the system does not let you
overwrite a running executable.

On Linux amd64, the stripped executable compares like this with the former
Java CLI, built as a native executable:

| Build         | Size    | `lvh-yard --help`, median of 100 runs |
| ------------- | ------- | ------------------------------------- |
| Go (this CLI) | 9.19 MB | 1.44 ms                               |
| Java, native  | 49 MB   | 7.3 ms                                |

The CLI embeds copies of `compose.yaml` and `.env.example` from
`cli/internal/templates/`. After you edit either root file, run
`mise run cli:templates` to refresh the copies. Commit both copies with the
root files. A unit test compares the raw bytes. When `init` writes the
Workstation Folder, it removes the dashboard build line and pins the image
to the CLI version, or `latest` for `dev`.

## Tests

There are three kinds of tests, as `docs/adr/0008` explains:

- A unit test starts no Spring context and no real system. Its class name
  ends in `Test`, and it lives in the package of the class that it tests.
- A component test starts the dashboard and goes through its HTTP API and
  pages. `FakeConsul` and `FakeDocker` stand in for Consul and Docker. Its
  class name ends in `ComponentTest`, and it extends `DashboardComponentTest`.
- An integration test uses at least one real system: Docker, Consul, Traefik,
  or another process such as `lvh-yard`. Its class name ends in `IT`.

The Go CLI in `cli/` has no component tests. Its kinds are:

- `go test ./...` runs the unit tests. They go through the command seam or the
  client seam, with no real process and no Docker.
- The integration test builds the executable and runs it against a fake
  dashboard. It has the build tag `integration` and runs with
  `mise run cli:smoke`.

Surefire runs unit and component tests. Failsafe runs integration tests.
`DockerContract` and `ConsulContract` run the same cases against each fake
and against the real system, so a fake cannot drift from the real one.

Give each edge case of a rule a unit test, for example the Route choice or an
input check. Give each feature a component test through the API or a page.
Keep an integration test only for what a fake cannot show. Examples are a
passing health check, the other Workstation's own dashboard, and Traefik.

While you work, run `mise run test:unit` or `mise run test:component`. Both
run in seconds, without Docker. Before you commit, run `mise run check`. It
builds the stylesheet, builds the dashboard from scratch and runs every test.
Both `test:unit` and `check` also run `go vet` and `go test ./...` in `cli/`.
`check` also runs `mise run cli:smoke`. This one integration test builds the Go
CLI, runs the executable against a fake dashboard, and checks the output, the
version and the exit codes. Its file has the build tag `integration`, so
`go test ./...` skips it.
The `gofmt` pre-commit hook rejects Go files that need formatting.
The dashboard tests log only warnings and errors. The full output of each test
class is in `dashboard/target/surefire-reports/` and
`dashboard/target/failsafe-reports/`.

The dashboard integration tests start a Yard of two federated Consul
datacenters with Testcontainers, so they need Docker. The second Workstation,
`ws-remote`, runs its own dashboard in the same JVM. The test classes run in
four JVMs at the same time, and each JVM starts its own test Yard. Before each
test, the test Yard goes back to its state just after the first setup. Both
Consuls get back their KV and registrations, and both Backups folders are
emptied.

A test checks one behavior. An integration test checks the status code, the
data, the CLI output or the Route that Traefik gets. It does not check exact
sentences, HTML classes or layout. A page test checks only that the key
element or link is there. The writing rules in `CLAUDE.md`, for
example which commands user text names, are not tested. When authors write
the text, they follow these rules.

After the tests pass, a manual check is needed in two cases only:

- A change to how a page looks. Take one screenshot of the changed page on
  `ws-a`.
- Behavior across two Workstations that the test Yard cannot show. Start
  `ws-a` and `ws-b` with `mise run dev:up` and try the change there.

If no source file changed after the last `mise run check` that passed, mise
skips the task. The source files are the two `pom.xml` files, `.mvn/`,
`package-lock.json`, `dashboard/openapi.yaml`, `compose.yaml`, `.env.example`,
the dashboard's `src/` folder, and the Go sources and module files in `cli/`. mise compares change times, not contents, so a `git checkout` also
starts a new run. A change outside these files does not start a new run, for
example a new Java version. To run every test anyway, use
`mise run --force check`.

`dashboard/openapi.yaml` describes the REST API that the CLI uses. If it
differs from the API that the dashboard serves, `OpenApiComponentTest` fails.
After you change the API, run `mise run openapi` to write it again. The task
runs that test with `-Dopenapi.update=true`, so it writes the file instead of
comparing it.

## Consolidation audit

After a large redesign, or about once a month, run `mise run audit`. It lists
the leads for a cleanup:

- long Trail learnings
- learnings that a newer one replaces
- learnings that name files or classes the code no longer has
- done specs that are still in `.scratch/`
- the number of tests of each kind, and test files over 800 lines

Each line is a lead to read, not a verdict. For example, a learning can name a
class to record why it was ruled out. To also time `mise run check` and count
its output lines, run `mise run audit --check`. Compare the output with the
last run to see what grew.

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
3. It runs the CLI tests and builds `lvh-yard` for each platform.
4. It attaches the archives and their `SHA256SUMS` to a GitHub release. The
   release notes list the commits since the last tag.

When the image and every archive are ready, the release appears. After the
first release, make the package public once in its settings on GitHub.

The `cli` job runs on one `ubuntu-latest` runner, without C code. It
cross-compiles stripped executables for `linux-amd64`, `linux-arm64` and
`macos-arm64`, then checks `--version` on the Linux amd64 executable. The
version drops the tag's leading `v`, so tag `v0.1.0` prints `lvh-yard 0.1.0`.

The `lvh-yard-archives` workflow artifact holds
`lvh-yard-<version>-<platform>.tar.gz` for each platform. Each archive holds
`lvh-yard` and `LICENSE`. The `release` job downloads the artifacts that match
`lvh-yard-*`, and writes `SHA256SUMS` for the three archives.

No run on a hosted test tag has checked the release workflow. The Linux arm64
and macOS arm64 executables need separate runtime checks.
