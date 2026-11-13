# Development

This page is for people who work on LVH Yard itself. To install and use LVH
Yard, read the [user guide](user-guide.md). [GLOSSARY.md](../GLOSSARY.md)
defines the words, and [docs/adr/](adr/) records the design decisions.

## Requirements

You need Docker with Compose, JDK 25 and [mise](https://mise.jdx.dev/). The
dashboard build selects the JDK through a Maven toolchain. mise runs the tasks
and installs Node for Tailwind CSS and Go for the CLI.

## Running from the sources

Use [A Yard on one computer](#a-yard-on-one-computer) to start Consul and
Traefik, then run either dashboard from the sources. The tasks create private
Workstation Folders in `dev/ws-a/` and `dev/ws-b/`. Do not use the
repository root as a Workstation Folder: `ws` repairs file modes recursively,
which removes executable modes from checkout files.

To try your sources on your own Workstation, stop its dashboard container if it
runs, and use `mise run dashboard:run`. It builds `app.css` and runs
`spring-boot:run` with the `.env` and `lvh-yard.yaml` of `~/.config/lvh-yard`.
Consul and Traefik keep running from Compose. Use `--dir <folder>` for another
Workstation Folder.

To try your sources on another Workstation, copy the CLI and the dashboard
image there through ssh. The other Workstation needs ssh, Docker and the
folder for the CLI, but no registry. Both are built for the system and CPU of
this computer:

```sh
mise run install:ssh user@host
```

The CLI goes to `~/.local/bin/lvh-yard` on the other Workstation. The image
keeps the tag `ghcr.io/ppalazon/lvh-yard-dashboard:latest`, the default image of
`compose.yaml`. On the other Workstation, unset `YARD_DASHBOARD_IMAGE` in `.env`
and run `lvh-yard ws compose recreate`. For this computer, use
`mise run install:local`.

## A Yard on one computer

Two test Workstations, ws-a and ws-b, can run side by side on loopback.
`dev:up` builds the CLI and uses `ws init` and `ws set` to make
`dev/ws-a/` and `dev/ws-b/`. They use the same Gossip Key and join each
other through their loopback WAN ports. ws-b uses the Base Domain `localhost`.
Later runs keep the folders and their settings, including certificates.
`ws compose start` starts Consul and Traefik with `COMPOSE_PROFILES=traefik`.
This explicit profile selection leaves the dashboard for `dev:dashboard`,
which runs it from the sources.

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

Each Workstation Folder holds its own `backups/`, Consul data and TLS files.
Git ignores both folders. `dev:dashboard` reads that Workstation's `.env`
and `lvh-yard.yaml`. `dev:down` uses `ws compose stop` and keeps the files.
The dashboard tests use a new temporary folder for each test JVM, through
the `lvh-yard.backups.path` setting.

## Building the CLI

The CLI is a Go program in `cli/`. It uses the standard library, Cobra, a
YAML module and a PKCS#12 module.

```sh
mise run cli:build      # builds cli/target/lvh-yard
mise run cli:install    # builds it and copies it to ~/.local/bin/lvh-yard
mise run install:local  # runs cli:install, then builds the dashboard image
```

`mise run cli:build` builds without C code and strips debug data. The
executable reports `dev` unless `VERSION` is set at build time. `cli:install`
writes a new file and then moves it, because the system does not let you
overwrite a running executable.

On Linux amd64, the stripped executable compares like this with the former
Java CLI, built as a native executable:

| Build         | Size    | `lvh-yard --help`, median of 100 runs |
| ------------- | ------- | ------------------------------------- |
| Go (this CLI) | 9.19 MB | 1.44 ms                               |
| Java, native  | 49 MB   | 7.3 ms                                |

The source templates are `config/compose.yaml`, `config/.env.example`,
`config/00-lvh-yard.hcl`, `config/10-tls.hcl`, `config/10-tls.yaml` and
`config/lvh-yard.yaml`. The CLI embeds copies from `cli/internal/templates/`.
After you edit a source, run `mise run cli:templates` to refresh the copies.
Commit the copies with the source files. A unit test compares the raw bytes.
When `ws init` writes the Workstation Folder, it pins the dashboard image to
the CLI version, or `latest` for `dev`. The user template has no build setting.

After `dev:up`, run this from the repository root to build the dashboard with Compose:

```sh
docker compose --project-directory dev/ws-a --env-file dev/ws-a/.env \
  -f dev/ws-a/compose.yaml -f dev/compose.yaml build dashboard
```

The CLI creates Workstation Folders with mode 700 and files with mode 600.
It creates `backups/` and `consul-data/` before Compose starts, so Docker
does not create them as root. Consul uses the user and group in `.env`
(`YARD_UID` and `YARD_GID`) and stores its data in `consul-data/`.
The CLI mode tests cover new folders and repair of existing folders.

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
Both `test:unit` and `check` also run `go vet`, golangci-lint and `go test ./...`
in `cli/`. The golangci-lint run in these two tasks uses only the linters that
report nothing today. `mise run quality:go` runs all of them.
`check` also runs `mise run cli:smoke`. This one integration test builds the Go
CLI, runs the executable against a fake dashboard, and checks the output, the
version and the exit codes. Its file has the build tag `integration`, so
`go test ./...` skips it.

The real-Docker TLS Yard test builds the CLI and creates two Workstation
Folders with `ws init` and `ws tls`. Build the dashboard image from this
checkout, then run from `cli/`:

```sh
YARD_TEST_DASHBOARD_IMAGE=lvh-yard-dashboard:test go test -tags dockerintegration -count=1 -run TestWorkstationTLSYard ./cmd/lvh-yard
```

The test starts the generated Compose projects and removes them when it
finishes. It needs Docker Compose and free local TCP and UDP ports.
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
`package-lock.json`, `dashboard/openapi.yaml`, the templates in `config/`,
the dashboard's `src/` folder, and the Go sources and module files in `cli/`. mise compares change times, not contents, so a `git checkout` also
starts a new run. A change outside these files does not start a new run, for
example a new Java version. To run every test anyway, use
`mise run --force check`.

`dashboard/openapi.yaml` describes the REST API that the CLI uses. If it
differs from the API that the dashboard serves, `OpenApiComponentTest` fails.
After you change the API, run `mise run openapi` to write it again. The task
runs that test with `-Dopenapi.update=true`, so it writes the file instead of
comparing it.

## Code quality

`mise run quality` runs the slow code checks. It is not part of
`mise run check`. It runs three tasks:

- `quality:go` runs golangci-lint on `cli/`. The rules are in
  `cli/.golangci.yml`.
- `quality:java` runs SpotBugs and PMD on the dashboard, with every PMD rule of
  `dashboard/pmd-ruleset.xml`. The reports are
  `dashboard/target/spotbugsXml.xml` and `dashboard/target/pmd.xml`.
- `quality:mutation` runs PIT with the dashboard unit tests only. It changes
  the code of a few classes, and a unit test that still passes means that the
  tests miss that change. It fails when the score of the four classes is under
  the `mutationThreshold` in `dashboard/pom.xml`. Raise that number when the
  score grows. The report is `dashboard/target/pit-reports/index.html`.

`./mvnw verify`, and so `mise run check`, also runs SpotBugs and PMD, and
fails on a finding. SpotBugs skips what `dashboard/spotbugs-exclude.xml` lists.
PMD runs only the rules of `dashboard/pmd-gate.xml`: unused private methods,
fields, local variables and parameters. [design.md](design.md) explains the
findings.

`ArchitectureTest` is a unit test of the package rules in
[design.md](design.md): no package cycles, and `consul`, `docker` and `events`
import no other feature package. Two of its rules are frozen. Their known
violations are in `dashboard/src/test/resources/archunit_store`, and a new
violation fails the test. Fix a violation and ArchUnit drops it from the store
the next time the test runs. Do not add one by hand.

## Continuous integration

The CI workflow (`.github/workflows/ci.yml`) runs on each pull request and on
each push to `main`. It has three jobs, on `ubuntu-latest`:

- `check` runs `mise run check`.
- `format` runs `mise run fmt`.
- `secrets` runs `mise run secrets` on the whole history.

macOS and Windows have no job. The only macOS file is the CLI, and the release
workflow cross-compiles it.

## Secrets

[gitleaks](https://github.com/gitleaks/gitleaks) looks for keys and tokens.
It runs in three places, all with the rules of `.gitleaks.toml`:

- The pre-commit hook scans the staged changes. Install it once with
  `mise run hooks`.
- `mise run secrets` scans the working tree and the whole git history.
- The `secrets` job of CI runs the same task.

`.gitleaks.toml` allows the throwaway gossip key in `dev/workstations.sh`
and the CLI tests, including old files in Git history. They only join the
test Workstations on loopback. Never put a gossip key
of a real Yard in a file of this repository. `.env` is ignored by Git for this
reason. If gitleaks finds a real secret, remove it, rotate it, and do not just
allow it.

## The landing page

The landing page is one static page in `docs/site/`: `index.html`, `site.css`
and `assets/`. `site.css` uses Tailwind CSS and Basecoat with the colors of
`dashboard/src/main/css/theme.css`, so the page and the dashboard look alike.
The page also loads the dashboard's `theme.js` for its dark theme. The site
also serves `install.sh`, at `https://lvhy.cc/install.sh`.

```sh
mise run site:build    # writes docs/site/dist
mise run site:serve    # builds it and serves http://localhost:8800
```

The Pages workflow (`.github/workflows/pages.yml`) builds the page and
deploys it to `https://lvhy.cc/` when `docs/site/` changes on `main`. Once, in
the repository settings, set **Pages > Source** to **GitHub Actions** and
**Pages > Custom domain** to `lvhy.cc`. The DNS records are in Cloudflare, set
to DNS only.

The screenshots in `docs/site/assets/` are of the dashboard of a test Yard
with demo Services. The README uses them too. To take them again, start the
test Workstations with `mise run dev:up`, create the Services with
`lvh-yard`, and take the pictures in the light and the dark theme.

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

`docs/site/install.sh` reads `SHA256SUMS` to find the archive name and the
version, so it needs no GitHub API. If you rename the archives or the platforms
in the `cli` job, change `platform()` in it too.

Before the first public release, prove this workflow on a private repository.
Push a test tag such as `v0.0.0-rc.1`, and check the image, the three
archives and `SHA256SUMS` of the release. Then run `--version` on the Linux
arm64 and macOS arm64 executables on their own platforms. Delete the test
release, the tag and the test image before you tag `v0.1.0`.

Versions follow [Semantic Versioning](https://semver.org/). Before 1.0.0, a
minor release can change how LVH Yard works. The release notes are the
generated list of commits, so write each commit message to say what the
commit does.
