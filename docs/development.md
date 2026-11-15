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

The same Go CLI cross-compiles for Windows 11 x64. From `cli/` on Linux:

```sh
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build ./...
GOOS=windows GOARCH=amd64 go vet ./...
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-X main.version=9.8.7" -o target/lvh-yard.exe ./cmd/lvh-yard
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go test -c -tags integration -o target/smoke.test.exe ./cmd/lvh-yard
```

Copy both executables to the Windows test folder. No Go installation is
needed there. In Windows PowerShell 5.1, from that folder:

```powershell
$env:YARD_TEST_EXECUTABLE = (Resolve-Path .\lvh-yard.exe).Path
.\smoke.test.exe "-test.count=1" "-test.v"
$LASTEXITCODE
```

The version must match `9.8.7`, which the executable checks expect.

The unit tests also run natively on Windows. `go test -c` with a folder after
`-o` writes one `<package>.test.exe` for each package that has tests:

```sh
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go test -c -o target/unit/ ./internal/...
```

Run each binary in the folder of its package, as `go test` does, because some
tests read files by a relative path. CI does this: see
[Continuous integration](#continuous-integration).

Two tests check what another ordinary user can do with the private files of a
Workstation Folder. They ask Windows, not the ACL text. The unit test
`TestWindowsProtectionDeniesAnotherUserByEffectiveAccess` uses a token of the
current user with that user and Administrators switched to deny only, and runs
everywhere. The integration test
`TestBuiltExecutableProtectsTheWorkstationFolderFromAnotherUser` logs on as a
real second account. It skips unless you name that account. Run it as the
owner, with a second ordinary account of the computer:

```powershell
$env:YARD_TEST_EXECUTABLE = (Resolve-Path .\lvh-yard.exe).Path
$env:YARD_TEST_OTHER_USER = 'second-account'
$env:YARD_TEST_OTHER_PASSWORD = 'its password'
.\smoke.test.exe "-test.count=1" "-test.v" "-test.run" ProtectsTheWorkstationFolder
```

Platform-specific process operations live in `process_unix.go` and
`process_windows.go`, behind the existing `Process` interface.
The Windows process code uses the console API for terminal detection.
It launches editors directly and renders copied commands for PowerShell.
Unix editor commands keep their shell behavior.

On Linux amd64, the stripped executable compares like this with the former
Java CLI, built as a native executable:

| Build         | Size    | `lvh-yard --help`, median of 100 runs |
| ------------- | ------- | ------------------------------------- |
| Go (this CLI) | 9.19 MB | 1.44 ms                               |
| Java, native  | 49 MB   | 7.3 ms                                |

The source templates are `config/compose.yaml`, `config/compose.windows.yaml`,
`config/traefik-windows.sh`, `config/.env.example`, `config/.env.windows.example`,
`config/00-lvh-yard.hcl`, `config/10-tls.hcl`, `config/10-tls.yaml` and
`config/lvh-yard.yaml`.
The CLI embeds copies from `cli/internal/templates/`.
After you edit a source, run `mise run cli:templates` to refresh the copies.
Commit the copies with the source files. A unit test compares the raw bytes.
`compose.windows.yaml` is a copy of `compose.yaml` with the changes that Docker
Desktop needs. Template tests keep the keys and variable defaults that the two
share equal. A real difference needs a named reason in
`windows_templates_test.go`. Each platform embeds only its own Compose file and
`.env` template.
When `ws init` writes the Workstation Folder, it pins the dashboard image to
the CLI version, or `latest` for `dev`. The user template has no build setting.

After `dev:up`, run this from the repository root to build the dashboard with Compose:

```sh
docker compose --project-directory dev/ws-a --env-file dev/ws-a/.env \
  -f dev/ws-a/compose.yaml -f dev/compose.yaml build dashboard
```

On Linux and macOS, the CLI creates Workstation Folders with mode 700 and files
with mode 600. It creates `backups/` and `consul-data/` before Compose starts,
so Docker does not create them as root. Consul uses the user and group in
`.env` (`YARD_UID` and `YARD_GID`) and stores its data in `consul-data/`.
The CLI mode tests cover new folders and repair of existing folders.

### Windows runtime development

Windows runtime acceptance is not complete. The
[Windows setup draft](user-guide.md#windows-workstation-setup) owns the
Docker Desktop prerequisites, native storage rules, and the reset, Backup
transfer and complete removal procedures.
The repository's developer tasks do not imply a native Windows build toolchain.
Build on Linux and copy the CLI and dashboard image for native runtime checks.

The Windows Workstation Folder holds configuration, certificates and Backups.
Consul state lives in a named volume scoped to the Compose project.
A one-shot container initializes its Linux ownership before Consul starts.
Long-lived Consul and dashboard containers stay non-root.
Direct Compose startup must use the generated files without a launch-time
overlay or environment preparation by the CLI.
`templates.Compose` selects `compose.windows.yaml` on Windows.
The generated `traefik/00-lvh-yard-windows.sh` takes two identical source
snapshots before it publishes Linux-local files. It switches the complete
TLS directory before it publishes configuration or touches unchanged files.
It preserves source YAML and TOML and propagates deletions.
The Windows dashboard starts Java with `-Djava.net.preferIPv4Stack=true`.
Without it, the measured Desktop runtime refused Java loopback connections
that curl reached from the same container. Unix launch settings stay unchanged.
The generated Windows setup also sets internal `YARD_IPV4_ONLY=true`.
It rejects IPv6 target addresses and reports unsupported IPv6 Reachability.

The dashboard's internal `YARD_INSTANCE_HOST` setting maps to
`lvh-yard.instance-host` in `dashboard/src/main/resources/application.yaml`.
It sets three things:

- The default host of Static Instances.
- The host that Backups omit and that an import restores.
- The Yard address of wildcard-published Bound Instances.

Local wildcard mappings still use loopback. Explicit Instance hosts and
published bind addresses stay unchanged.
An unset or blank value uses Consul's agent address, preserving Unix defaults.
This setting adds no HTTP API field or domain property.
The Windows Compose file reads `YARD_INSTANCE_HOST` from `.env`. If it is empty,
the file uses `YARD_BIND_ADDRESS`, the LAN address that Consul advertises.
Consul always listens on `0.0.0.0` inside Docker Desktop.
`ws init` also writes `COMPOSE_PROJECT_NAME` to the Windows `.env`.
`composeProjectName` makes it from the folder name and a short code of the full
path, so two folders never share the `consul-data` volume.

`InstanceHostComponentTest` checks the configured host through the existing
dashboard API, pages, Backup export/import and Traefik configuration.
These deterministic tests do not establish Docker Desktop behavior.
Runtime acceptance needs an ordinary Windows user and a second LAN computer.
Check effective ACL privacy from another ordinary account, direct Compose
startup, persistent state and local listener isolation.
Check native-loopback routing through real Traefik requests separately from
Health and Reachability results. Record only observed outcomes.

To check persistence, start two fresh Workstations with different
`COMPOSE_PROJECT_NAME` values and ports. Save a Backup export of each with
`lvh-yard export`, ignoring its `at:` line. Run stop and start, recreate, an
image upgrade and a Docker Desktop restart on one. After each, its export, its
Consul node ID and the creation time of its `consul-data` volume must not
change. Then run `docker compose down --volumes` in the other folder. Only that
project's volumes may disappear from `docker volume ls`, and the first
Workstation's export must stay the same. Linux Workstations in other
folders and ports serve as the other platform for Backup transfer.

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
- The integration tests build the executable and run it against a fake
  dashboard, native recording editors and a recording Docker executable.
  They also run printed commands through the platform shell. The tests have
  the build tag `integration` and run with `mise run cli:smoke`.

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
report nothing today. It runs twice, once for this computer and once with
`GOOS=windows`, so the gate also covers the `_windows.go` files and the Windows
tests. `mise run quality:go` runs all of them for this computer only.
`check` also runs `mise run cli:smoke`. These tests check output, version,
exit codes, editor file handling, subprocess arguments and shell quoting.
Their build tag is `integration`, so `go test ./...` skips them.

On Windows, the integration tests use each PowerShell executable found on
`PATH`. They report an absent `pwsh.exe`; a passing run with only Windows
PowerShell 5.1 does not prove PowerShell 7 behavior. Completion checks load
the generated script and use `TabExpansion2`.

Recording executables cannot prove Docker Desktop behavior or a GUI editor.
Check prompts through a real Windows console, and check noninteractive input
separately. Start a real Compose project, follow its logs, press Ctrl+C and
run another shell command. Check the default editor on the Windows desktop.
Native CLI evidence lives under `.scratch/windows-11/evidence/`.
Full Windows Workstation support still needs its remaining runtime gates.
Native CLI evidence covers both supported PowerShell versions.

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
  `gosec` also runs in `cli:check`, so a new unsuppressed security finding
  fails the normal checks.
- `quality:java` runs SpotBugs and PMD on the dashboard, with every PMD rule of
  `dashboard/pmd-ruleset.xml`. The reports are
  `dashboard/target/spotbugsXml.xml` and `dashboard/target/pmd.xml`.
- `quality:mutation` runs PIT with the dashboard unit tests only. It changes
  the code of a few classes, and a unit test that still passes means that the
  tests miss that change. It fails when the score of the selected classes is under
  the `mutationThreshold` in `dashboard/pom.xml`. Raise that number when the
  score grows. The report is `dashboard/target/pit-reports/index.html`.

`./mvnw verify`, and so `mise run check`, also runs SpotBugs and PMD, and
fails on a finding. SpotBugs skips what `dashboard/spotbugs-exclude.xml` lists.
PMD runs only the rules of `dashboard/pmd-gate.xml`: unused private methods,
fields, local variables and parameters. [design.md](design.md) explains the
findings.

Managed CLI file operations use `os.Root` at the selected Workstation Folder,
not a derived subfolder. The command tests in `ws_security_test.go` check
escaping links and a relative link that stays inside. The
[user guide](user-guide.md#running-on-a-workstation) states the file-access policy.
Intentional user-selected paths and subprocesses have local `#nosec` comments
with a rule ID and a reason. Keep each exception beside the operation it permits.

`ArchitectureTest` is a unit test of the package rules in
[design.md](design.md): no package cycles, `consul`, `docker` and `events`
import no other feature package, and only `consul` uses the Spring web client.
Pure domain rules import no Spring. All rules reject every violation; none uses
a frozen baseline. `OutOfRangeConfiguration` binds the out-of-range settings and
supplies the pure `OutOfRange` rule.

## Continuous integration

The CI workflow (`.github/workflows/ci.yml`) runs on each pull request and on
each push to `main`. It has five jobs. Three run on `ubuntu-latest`:

- `check` runs `mise run check`.
- `format` runs `mise run fmt`.
- `secrets` runs `mise run secrets` on the whole history.

Two jobs run the Windows CLI on a real Windows system:

- `windows-package` runs on `ubuntu-latest`. It packages the versions 9.8.7
  and 9.8.8 with `dev/package-cli.sh`, writes a `SHA256SUMS` file for each as
  the release does, builds the integration tests for Windows with
  `go test -c -tags integration`, builds the unit tests of `cli/internal/` with
  `go test -c`, and uploads the folder. Beside the unit test binaries, it
  copies the files that the tests read: the package folders without Go
  sources, `config/` and `dashboard/openapi.yaml`. The Windows job needs no Go
  and no Unix shell.
- `windows` runs on `windows-latest`, once in Windows PowerShell 5.1 and once
  in PowerShell 7. It runs `dev/test-windows-native.ps1` in that shell.

`dev/test-windows-native.ps1` does these steps:

1. It removes each PATH folder with a Unix tool (`sh`, `id`, `vi`, Git Bash
   and others) from its own process. It fails when one of these tools still
   resolves.
2. It extracts `lvh-yard.exe` from the packaged ZIP and runs the integration
   tests of `cli/cmd/lvh-yard` with that executable. The tests must pass in
   both `powershell.exe` and `pwsh.exe`. None may be skipped, except the test
   that logs on as a second account: a hosted runner has one account. Set
   `YARD_TEST_OTHER_USER` and `YARD_TEST_OTHER_PASSWORD` to make that test
   run, and it must then pass.
3. It runs each unit test binary in the folder of its package. All must pass,
   and four Windows-only tests must have run, so a build constraint cannot
   hide them.
4. It runs `dev/test-install-windows.ps1` with `docs/site/install.ps1` and the
   packaged archives. The script seeds the user PATH with a real `%USERPROFILE%`
   entry, once as `REG_EXPAND_SZ` and once as `REG_SZ`, and checks that the
   installer keeps its text and kind.
5. It removes its temporary files. The acceptance script restores the user
   PATH, text and kind.

The `windows` job starts from a clean checkout. It installs nothing. It uses
the Windows PowerShell 5.1 and the PowerShell 7 of the runner image, and Git
for the checkout. It does not change firewall rules, Docker settings, the
machine PATH or the execution policy. The policy is `Bypass` only on the
command line of each process.

The native job is not Windows 11 acceptance. It runs on Windows Server. It
checks the executable, the shell quoting, the completion and the installer. It
checks nothing about Docker Desktop, Workstation containers, routing, firewall
access or a second computer. Those checks need a real Windows 11 computer and
an ordinary user.

The runner account is an administrator. The job passes `-AllowAdministrator`,
and the output says that the run does not show ordinary-user behavior. Without
that switch, both scripts stop for an administrator. To check ordinary-user
behavior, copy the folder that `windows-package` uploads, `dev/` and
`docs/site/install.ps1` to Windows. Then run `dev/test-windows-native.ps1` as
an ordinary user in each shell.

macOS has no job. The only macOS file is the CLI, and the release workflow
cross-compiles it.

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
The page also loads the dashboard's `theme.js` for its dark theme.
The site build copies both `install.sh` and `install.ps1` into
`docs/site/dist`, for `/install.sh` and `/install.ps1` on the deployed site.

The hero cycles through service types and locations with CSS. Service types
change every 2 seconds, and locations change every 2.6 seconds. Each reel
reserves space for its longest word and centers the text. Hover over the
heading to pause both reels. With reduced motion,
the heading stays on its first words. Screen readers get one fixed heading
with all the words.

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

The `cli` job calls `dev/package-cli.sh` on one `ubuntu-latest` runner,
without C code. It cross-compiles stripped executables for `linux-amd64`,
`linux-arm64`, `macos-arm64` and `windows-amd64`, then checks `--version` on
the Linux amd64 executable. The version drops the tag's leading `v`, so
tag `v0.1.0` prints `lvh-yard 0.1.0`.

The `lvh-yard-archives` workflow artifact holds
`lvh-yard-<version>-<platform>.tar.gz` for Unix platforms and
`lvh-yard-<version>-windows-amd64.zip` for Windows. Each archive holds the
executable and `LICENSE`; Windows names its executable `lvh-yard.exe`.
The `release` job downloads the artifacts that match `lvh-yard-*`, writes
`SHA256SUMS` for all four archives, and uploads the archives and manifest.

Both installers read `SHA256SUMS` to find the archive name, so they need no
GitHub API. If you rename an archive or platform, update its installer too.
The PowerShell installer stages the verified executable beside its
destination and uses Windows atomic file replacement. It never deletes the
installed executable to make room for an upgrade.

To build the release archives locally:

```sh
mise exec -- sh dev/package-cli.sh 9.8.7
```

For native installer acceptance, build versions `9.8.7` and `9.8.8` with that
script. Preserve each version's four archives in a separate folder named for
the version. In each folder, generate the manifest with the release job's
command:

```sh
sha256sum lvh-yard-*.tar.gz lvh-yard-*.zip > SHA256SUMS
```

Copy those two folders, `docs/site/dist/install.ps1` from `mise run site:build`,
and `dev/test-install-windows.ps1` to an ordinary Windows user's test folder.
In each supported PowerShell version, run:

```powershell
.\test-install-windows.ps1 -Installer .\install.ps1 -Artifacts .\artifacts -Root "$PWD\acceptance"
```

Use a new absolute `Root` path on each run. The test controls only downloads,
using the actual packaged archives and manifests. File extraction,
replacement, locking and user PATH changes are native. The test restores
the original user PATH and removes its acceptance root. Its fresh shell
rebuilds PATH from the registry because an SSH service can retain an older
environment.

The Windows public installation entry point still needs maintainer publication.
A local site build and native installer test do not prove a live Pages
deployment or a GitHub release upload. Do not announce Windows support until
the maintainer approves publication and the remaining Windows acceptance.

Before the first public release, prove the publish workflow on a private
repository. Push a test tag such as `v0.0.0-rc.1`, and check the image, all four
archives and `SHA256SUMS` of the release. Then run `--version` on the Linux
arm64 and macOS arm64 executables on their own platforms. Delete the test
release, the tag and the test image before you tag `v0.1.0`.

Versions follow [Semantic Versioning](https://semver.org/). Before 1.0.0, a
minor release can change how LVH Yard works. The release notes are the
generated list of commits, so write each commit message to say what the
commit does.
