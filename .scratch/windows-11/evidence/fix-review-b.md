# Review fix batch B

Branch `win11/fix-review-b`, from `windows-11` at 2e94acd. Linux host only. No VM, no Docker run.
Items refer to `review-early.md`. Skipped by instruction: 22, 25, 26, 11, 12, 13.

## Observed results

- `mise run test:unit`: passed (Maven unit tests, `cli:check`: go vet, golangci-lint for Linux and for Windows, go test).
- `mise run check`: passed, exit 0 (css, `./mvnw -q clean verify`, `cli:check`, `cli:smoke`).
- `GOOS=windows GOARCH=amd64 go vet ./...` in `cli/`: no output. This compiles the Windows files and their tests.
  Windows tests did not run. No Windows computer was used.
- `docker compose config` on the Linux host, on `config/compose.windows.yaml` (no `up`):
  - `YARD_BIND_ADDRESS=192.168.1.5` only: dashboard `YARD_INSTANCE_HOST: 192.168.1.5`.
  - Plus `YARD_INSTANCE_HOST=10.1.1.1`: dashboard `YARD_INSTANCE_HOST: 10.1.1.1`. Consul still advertises `$${YARD_BIND_ADDRESS}`.
  - Neither set: refused with "set the native LAN IPv4 address in .env".
  - `COMPOSE_PROJECT_NAME=x-1` gave `name: x-1`.
- Narrow dashboard tests (`./mvnw -q -pl dashboard test -Dtest=...`): `AliasesComponentTest` 13, `ArchitectureTest` 6,
  `IPv4OnlyComponentTest` 20, `InstanceHostComponentTest` 4, `OpenApiComponentTest` 16. No failure.
- Mutation check of the Compose test: changing `restart` and a `YARD_STACK_TRACES` default in the Windows copy failed
  `TestWindowsComposeKeepsTheSharedKeysOfUnixCompose` and `TestWindowsComposeKeepsTheVariableDefaultsOfUnixCompose`.
  The copy was restored, and the tests passed again.

## Items

- **5** `config/.env.windows.example` is new, with a copy in `cli/internal/templates/`. `mise run cli:templates` copies it.
  `TestCommittedTemplatesMatchRepositoryBytes` lists it. `strings.NewReplacer` is gone. Each platform embeds only its own
  Compose file and `.env` template, in `templates_unix.go` and `templates_windows.go`.
  `TestEnvSelectsPlatformTemplate` compares `Env()` with the platform file.
- **14** The Windows template keeps `#YARD_UID`, `#YARD_GID` and `#YARD_DOCKER_GID` commented out, with a comment that they do
  nothing there. `ws init` on Windows no longer fills them and gives no Docker group warning. The Windows `socketGroup` stub
  was removed. `TestWindowsEnvTemplateKeepsTheUnixSettings` checks the template.
- **9** Windows `.env` has `COMPOSE_PROJECT_NAME`. `ws init` writes it from the folder name (lowercase, other characters
  become `-`, at most 40 characters, `lvh-yard` when nothing valid remains) and 8 hex digits of a SHA-256 of the lowercased
  absolute path. Two folders with one name under different parents get different projects, and one folder always gives one
  name. Unix `.env` is unchanged (the byte parity and template-order tests pass). `compose_project_test.go` tests ASCII, spaces,
  apostrophes, uppercase, underscore, accent, mixed Unicode, all-Unicode and empty names, long names, path case,
  relative against absolute, and two parents with one name. All pass on Linux.
  `TestWindowsInitNamesTheComposeProjectOfEachFolder` (Windows only) is written, not run.
- **6** `compose.windows.yaml` advertises Consul from `YARD_BIND_ADDRESS` and takes the dashboard's `YARD_INSTANCE_HOST` from
  `.env` `YARD_INSTANCE_HOST`, falling back to `YARD_BIND_ADDRESS`. `ws init` writes `YARD_INSTANCE_HOST=` empty, so it follows
  the LAN address until set. New setting `instance-host` (IPv4 or empty) in `settings.go` and `ws set`. Unix `.env` has no such
  line, so `ws set instance-host` fails there with ".env has no setting"; the unknown-setting message says Windows only.
  Tests: `settings_test.go`, `ws_settings_test.go` (set, bad value, clear), and Windows-only
  `TestWindowsSetsTheInstanceHostApartFromTheAdvertisedAddress` (written, not run).
- **17** Moved behind per-OS names in `workstation_unix.go` and `workstation_windows.go`: `lanAddressRequired`,
  `platformWorkstationFiles`, `platformUpgradeServices`, `protectNewFolder`, `linkInFolderError`, `fillPlatformEnv`,
  `dockerGroupWarning`; plus the embeds in `templates_*.go`. The `runtime.GOOS` branches in `init.go`, `init_upgrade.go`,
  `ws_permissions.go` and `templates.go` are gone. `commandtest/platform.go` still uses `runtime.GOOS`: it is test support,
  not product code, so I left it.
- **19** Test, not an override file: `windows_templates_test.go` compares the services both files have (image, network mode,
  restart, profiles, environment values) and every variable default. Each difference needs a named reason in the test.
  It also checks each Compose variable has a `.env` template line.
- **20** `Service.withRouting` and a private `Service.with` replace the 15-component rebuild in `YardReader`; `withAliases`
  uses `with` too. `Service.givenInstances()` replaces the repeated sources-to-instances stream in `YardReader` and
  `ReachabilityTester.unsupportedOnly`. `withAliases` itself could not replace the `YardReader` rebuild: it changes sources and
  routing for Aliases, while the reader changes only the Route.
- **23** `docs/development.md` lists the three uses of `YARD_INSTANCE_HOST` as bullets. It also documents the Windows
  Instance host, the project name, the new template and the Compose test. `docs/user-guide.md` documents the new setting,
  the project name and the Instance host in the Windows sections.

## Needs a runtime check on the VM later

- Item 6: Compose with the nested default (`${YARD_INSTANCE_HOST:-${YARD_BIND_ADDRESS:?...}}`) on Docker Desktop for Windows:
  the dashboard gets the LAN address when `YARD_INSTANCE_HOST` is empty, and the other value when it is set.
- Item 9: a real `ws init` on Windows writes a valid `COMPOSE_PROJECT_NAME`, `docker compose` accepts it, and two
  folders with one name use two `consul-data` volumes. Also run the Windows-only tests added here.
