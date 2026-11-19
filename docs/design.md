# Design

This page is for people who work on LVH Yard itself. It shows how the code is
built today, what the checks found, and where the code should go. For the words
of LVH Yard, read [GLOSSARY.md](../GLOSSARY.md). For the commands, read
[development.md](development.md).

Numbers are from 2026-11-13. They show the start, not a rule.

## Words used here

| Word            | Meaning                                                                                        |
| --------------- | ---------------------------------------------------------------------------------------------- |
| Domain core     | Rules of LVH Yard. It knows no web, no Consul, no Docker.                                      |
| Use case        | One thing a person asks for, such as "register an Instance". It calls the rules and the ports. |
| Port            | An interface that the core owns. It says what the core needs from outside.                     |
| Driving adapter | Code that calls the core: web pages, REST controllers, CLI commands.                           |
| Driven adapter  | Code that the core calls through a port: `ConsulAgent`, `DockerEngine`.                        |

These words are about the code. `GLOSSARY.md` does not hold them.

```mermaid
flowchart LR
    subgraph driving[Driving adapters]
        web[HtmlFlow pages]
        rest[REST controllers]
    end
    subgraph core[Core]
        uc[Use cases]
        dom[Domain rules]
    end
    subgraph driven[Driven adapters]
        consul[ConsulAgent]
        docker[DockerEngine]
        files[Backups folder]
    end
    web --> uc
    rest --> uc
    uc --> dom
    uc -- port --> consul
    uc -- port --> docker
    uc -- port --> files
```

Every arrow points to the core, or goes out through a port that the core owns.

## SOLID, with this repo

| Principle             | Rule                                                      | Example here                                                                                                                | Fix                                                                                                                                                                 |
| --------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Single responsibility | A class has one reason to change.                         | `Registrations` (587 lines) checked input, parsed text and wrote to Consul.                                                 | Done: `InstanceRules` has the checks and no Spring. `Registrations` (309 lines) keeps the Consul writes.                                                            |
| Open/closed           | A new kind needs a new class, not an edit in many places. | `SourceKind` is a `switch` key in `InstanceSourcesCard` and `BackupExport`.                                                 | Done where the answer is the same for every kind: `SourceKind.describe`. The other `switch` statements draw different data for each kind, so they stay in the view. |
| Liskov substitution   | A subtype works wherever its parent works.                | Not checked. No tool covers it.                                                                                             | None.                                                                                                                                                               |
| Interface segregation | A class depends only on the methods it uses.              | `Consul` has 15 methods, and nine classes used it whole.                                                                    | Done: five roles, `ConsulKv`, `ConsulCatalog`, `ConsulFederation`, `ConsulRegistrations` and `ConsulWatching`. Each class holds the role it uses.                   |
| Dependency inversion  | The core owns the interface. The adapter follows it.      | `YardReader` imported `org.springframework.web.client.HttpServerErrorException`. `Consul` holds `@JsonProperty` wire types. | `ConsulAgent` now throws `ConsulException`, so no class outside `consul` imports the Spring web client. The wire types are next.                                    |

## Dashboard (Java)

### Today

```mermaid
flowchart LR
    web --> services
    web --> backups
    web --> profiles
    web --> reachability
    web --> status
    web --> events
    web --> consul
    backups --> services
    backups --> profiles
    backups --> consul
    services --> consul
    services --> docker
    services --> events
    services --> profiles
    profiles --> consul
    reachability --> services
    routes --> services
    routes --> profiles
    routes --> reachability
    status --> consul
    status --> docker
    status --> reachability
    status --> routes
    status --> events
```

The package graph had three cycles at first: `services`↔`profiles`,
`services`↔`reachability` and the base package↔`web`. They are gone, and
`ArchitectureTest` fails on a new one. What changed:

- `OutOfRange` and `TraefikSettings` moved to the base package, next to
  `Workstation`. They are settings that every package reads.
- `Problems` moved to `web`, where `ErrorPage` is.
- `ProfileUsage` is an interface of `profiles`. `Publications` implements it, so
  `RouteProfileEditor` no longer imports `services`.
- `ReachabilityController` holds the two reachability endpoints, in
  `reachability`. `ServicesController` and `WorkstationServicesController` no
  longer import `reachability`. The OpenAPI tag of both endpoints is now
  `reachability-controller`.
- `ServiceViews` builds the HTTP and HTTPS addresses of a Service, so
  `TraefikSettings` no longer imports `Service`.

- `services` is the hub: domain rules, Spring beans, four REST controllers and
  JSON DTOs in one package (about 60 files).
- `consul` and `docker` are real ports, with a fake and a contract test each
  ([ADR 0008](adr/0008-unit-component-and-integration-tests.md)). `events` is
  also a leaf.
- No port exists for Traefik (`TraefikStatus` and `ReachabilityTester` open
  their own connections) or for files (`BackupsFolder` uses `Files.*`). Neither
  has a second implementation or a fake, so a port would be a layer and no
  seam. They stay as they are.
- Time comes from a `java.time.Clock` bean, which six classes receive:
  `BackupsFolder`, `ContainerWatcher`, `FirstSetup`, `StatusReader`,
  `TraefikFetches` and `TraefikStatus`. A test sets it.
- No use-case layer exists, on purpose. The HTML and REST controllers call
  `Registrations`, `Publications`, `Aliases` and `YardReader` and catch their
  exceptions. These classes are the use cases, and a layer on top of them would
  only forward calls.
- `services` stays one package. `ArchitectureTest` already fails on a cycle, so
  the size of the package cannot hide a dependency.

### Findings

| Class                                              | Size                        | PMD                                                 | Problem                                                                                                                                                                                     |
| -------------------------------------------------- | --------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Registrations`                                    | 587 lines at first, 309 now | God class, total cyclomatic complexity 105 at first | Split: `InstanceRules` (301 lines, no Spring) has the input rules. The Consul writes stay.                                                                                                  |
| `YardReader`                                       | 431 lines at first, 169 now | God class, coupling 47 (limit 20) at first          | Split into `DatacenterReads` (the circuit breaker), `DatacenterServices` (a datacenter's Services and the hostname rule) and `InstanceMapping` (Consul entry to Instance, no Spring).       |
| `ImportPlanner`                                    | 505 lines at first, 81 now  | God class, coupling 34 at first                     | Split by Backup section: `ProfileImport`, `SourceImport` and `PublicationImport` plan their parts into a `PlanDraft`. `ImportPlanner` plans the Services and has six dependencies, not ten. |
| `ContainerBindings`                                | 390 lines at first, 159 now | God class, coupling 25 at first                     | Split: `ContainerBindingRules` (no Spring, no Consul) has the input rules, `ContainerSync` brings the Bound Instances in step with Docker, and `ContainerBindings` keeps the Consul writes. |
| `PagesController` and the Service page controllers | 3-6 dependencies each       | Handlers grouped by responsibility                  | The Catalog, Service detail, Service lifecycle, Static Instances, routing controls and Aliases have separate controllers. `ServicePageAccess` shares lookup and edit checks.                |
| `BackupsPage`                                      | 636 lines                   | God class                                           | One view class with 48 branches.                                                                                                                                                            |

SpotBugs found 80 problems and PMD found 150 when I first ran them. Most are
noise or style:

| Finding                                           | Count | Verdict                                                                                                                                                                                    |
| ------------------------------------------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| SpotBugs `EI_EXPOSE_REP` and `EI_EXPOSE_REP2`     | 75    | A record or class holds a list or map that the caller can change. Nothing changes them after the dashboard builds them, so `spotbugs-exclude.xml` skips both patterns.                     |
| SpotBugs `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | 3     | Fixed in `ConsulAgent` and `BackupsPagesController`. `BackupsFolder:93` is a false alarm: a child path always has a file name. `spotbugs-exclude.xml` lists it.                            |
| SpotBugs and PMD, unused private method           | 3     | `Registrations.notATcpPort` and `PagesController.notCreated` are deleted. `mise run check` fails on a new one.                                                                             |
| PMD `LiteralsFirstInComparisons`                  | 29    | `name.equals("x")` throws on a null name. Now `"x".equals(name)`. In the gate.                                                                                                             |
| PMD `UseLocaleWithCaseConversions`                | 13    | `toLowerCase()` follows the user's locale, so a Turkish computer turns `INSTANCE` into `ınstance`. Now `toLowerCase(Locale.ROOT)`. In the gate.                                            |
| PMD `PreserveStackTrace`                          | 6     | Not a fault. Each one turns a low-level error into a domain exception whose message already holds the cause text, or unwraps `ExecutionException` on purpose. The rule is not in the gate. |
| PMD `CompareObjectsWithEquals`                    | 9     | Not a fault. Each `==` compares a thread or an object by identity on purpose. The rule is not in the gate.                                                                                 |
| PMD `TooManyMethods`                              | 22    | Follows from the god classes.                                                                                                                                                              |

### Mutation tests

PIT changes the code and runs the unit tests only (`*Test`, not
`*ComponentTest`). A mutant that no test fails on survives. The component tests
cover the HTTP path, so these numbers are the unit tests alone. The table
compares the first run with the current unit tests:

| Class                               | Mutants | Killed at first  | Killed now | Survived now |
| ----------------------------------- | ------- | ---------------- | ---------- | ------------ |
| `Registrations` and `InstanceRules` | 138     | 23 (17%)         | 137 (99%)  | 1            |
| `BackupFormat`                      | 125     | 101 (81%)        | 114 (91%)  | 11           |
| `Routing`                           | 34      | 29 (85%)         | 34 (100%)  | 0            |
| `RouteBuilder`                      | 28      | 16 (57%)         | 28 (100%)  | 0            |
| `InstanceMapping`                   | 19      | new              | 19 (100%)  | 0            |
| All classes                         | 344     | 169 of 325 (52%) | 332 (97%)  | 12           |

`dashboard/pom.xml` sets `mutationThreshold` to 96, so `mise run quality` fails
when the score drops below it. `mise run quality:mutation` produces the counts
in this table in `dashboard/target/pit-reports/mutations.xml`. Every mutant has
test coverage. `Routing` has no survivors. The remaining survivors change
validation diagnostics or return values that callers cannot distinguish.
No mutant is excluded to raise the score.

On 2026-11-19, PIT also ran once over the component tests, which the gate does
not do, on the classes that the HTTP paths of Services, Publications, Aliases,
Container Bindings and the Backup import go through. It took nine minutes with
one thread, so it is a check to run by hand, and no `mise` task runs it.

| Class                   | Mutants | Killed now |
| ----------------------- | ------- | ---------- |
| `Aliases`               | 49      | 39         |
| `ContainerBindingRules` | 40      | 33         |
| `ContainerBindings`     | 31      | 24         |
| `ContainerSync`         | 36      | 36         |
| `ImportPlanner`         | 11      | 9          |
| `PlanDraft`             | 8       | 8          |
| `ProfileImport`         | 13      | 11         |
| `PublicationImport`     | 17      | 17         |
| `Publications`          | 40      | 37         |
| `Services`              | 21      | 14         |
| `SourceImport`          | 53      | 47         |
| All                     | 319     | 275 (86%)  |

26 of the 44 survivors and mutants without coverage remove a `checkHome`,
`checkName`, `checkTarget`, `checkRemote` or `checkPort` call. No test over
HTTP depends on those calls. They stay as the second check of public classes,
and `GuardsTest` now tests them on the classes: 24 of the 25 such mutants die
in the unit tests alone.
Two more gaps were real, and tests now cover them: `sameTarget` of the
Container Binding rules had no test, and no Backup import checked that the same
sources in another order give an Instance Priority update.

### Target

Ports and adapters inside each feature package. The packages keep their names.

- The `Consul` port is for testing and isolation. Consul stays the one
  registry ([ADR 0001](adr/0001-one-consul-datacenter-per-workstation.md) and
  [ADR 0004](adr/0004-route-profiles-travel-through-consul-kv.md)).
- `ConsulAgent` throws `ConsulException`, with `ConsulUnreachableException`
  (no answer in time) and `ConsulServerException` (a 5xx) for the two cases that
  mean a datacenter cannot be read now. Done.
- `Consul` is split by role into five interfaces, and `ArchitectureTest` fails on a
  class outside `consul` that holds all of `Consul`. Done.
- The wire types (Consul's JSON field names) are private to `ConsulAgent`, and
  the types of the port have no Jackson annotation. Done.
- Pure rules import no Spring: `Routing`, `ServiceWarning`, `OutOfRange`, the
  input checks.
- Use cases may keep `@Component`.
- The code has no package cycles, and `ArchitectureTest` checks it.

```mermaid
flowchart LR
    web --> services
    web --> backups
    web --> profiles
    backups --> services
    services --> profiles
    reachability --> services
    routes --> services
    status --> services
    services -- port --> consul
    services -- port --> docker
```

`ArchitectureTest` checks these rules, and no class outside `consul` may use the
Spring web client. All rules reject every violation, with no frozen baseline.
`OutOfRange` has no Spring dependency. `OutOfRangeConfiguration` binds its
settings with Spring's `Binder`, so configuration stays outside the rule.

## CLI (Go)

### Today

```mermaid
flowchart LR
    main[cmd/lvh-yard] --> command
    command --> client
    command --> templates
    command --> envfile
    command --> settings
    command --> composefile
    command --> tlsca
    envfile --> templates
    commandtest --> command
    clienttest --> client
```

- `command` had 4,307 lines in 34 flat files. It holds cobra commands, output,
  `docker compose` calls and prompts. The pure logic moved out into four
  packages that read and write no file and print nothing:
  - `envfile`: the text of a `.env` file.
  - `settings`: the name of each Workstation setting and what it accepts, with one
    rule for each in a table, in place of one switch.
  - `composefile`: the image values of a `compose.yaml`, and their replacement.
  - `tlsca`: the Workstation CA, the Consul and Traefik certificates, the Client
    Certificates, the password of a CA key, and the checks of a certificate.

  Each has its own tests. For `settings` and `composefile`, a temporary test
  compared the new code with the old for many inputs, and the answers were the
  same.

- `client` is clean. It imports no other package of the CLI.
- `command.Process` is the only interface. It has 7 methods and mixes
  environment, input, terminal, LAN address, editor, `exec` and `id`.
- Each command called `client.New(options.DashboardAddress())` (24 call sites),
  so each test started its own `httptest` server. Now `command.Dashboard` is an
  interface of the 26 methods that the commands use. `Options.Dashboard` builds
  it, and `client.Client` is the default. `commandtest.Harness.Dashboard` sets a
  fake. `TestStatus` uses one. The other command tests still use `httptest`,
  which also checks the JSON, and they can move one at a time.
- `ws yard peers` reads Consul with `Options.ConsulHTTP`, so a test gives it a
  fake client. `dashboard.go` still reads `.env` with `os.ReadFile`, which a
  test reads from a temporary folder.

### Findings

golangci-lint first found 564 problems, 401 of them outside the tests. The table
is that first run.

| Linter            | Not in tests | What                                                                                                                                                                   |
| ----------------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| errcheck          | 164          | Unchecked `fmt.Fprint*` to a writer (159), `Close`, `Remove`, `Write`.                                                                                                 |
| revive            | 162          | 25 capitalized error strings, 23 unused parameters, missing comments.                                                                                                  |
| staticcheck       | 36           | 33 of them are ST1005, capitalized error strings.                                                                                                                      |
| gocyclo (over 15) | 18           | Worst: `NewRemove` 40, `init.create` 38, `upgradeInit` 33, `checkWSSetting` 31, `newWSTLSIssueUser` 30, `newWSTLSIssueRoles` 29, `newWSYardShare` 27, `NewCatalog` 26. |
| gosec             | 16           | 11 file reads from a variable path, 3 `exec` calls.                                                                                                                    |
| modernize         | 4            | `errors.AsType`, `SplitSeq`, `slices.Contains`.                                                                                                                        |
| errorlint         | 1            | `root.go:84` compares errors with `!=`.                                                                                                                                |

Capitalized error strings are the style of the CLI messages, so `cli/.golangci.yml`
turns off ST1005 and the revive rule for them. It also skips unchecked
`fmt.Fprint*` writes. `errcheck`, `errorlint`, `modernize` and the other
small findings are fixed, and `mise run check` fails on a new one. It also turns
off three revive rules that say nothing for a command (doc comments on exported
names, package comments and unused parameters), and `gosec` in the tests.
`mise run quality:go` lists the findings of the linters that are not in the
gate. `gocyclo` and `funlen` are in the gate for the code, and are off in
`_test.go` files: a table of cases makes a test long and branchy without making
it hard to read. `gosec` runs in the normal gate, with local exceptions for
intentional file access and subprocesses.
[development.md](development.md#code-quality) explains the policy.

### Target

- The dashboard interface is in `command`, next to where it is used, and is
  injected through `Options`, as `Process` is. It is one interface, not one for
  each command, so it is wide. A test fake embeds it and gives only the methods
  that its command calls. Split it by command if a fake gets in the way.
- Move pure logic out of the cobra closures into packages. Done for the four
  above, and `checkWSSetting` (31) and `composeImages` (20) left the gocyclo
  list. `NewRemove` (40), `init.create` (38) and `upgradeInit` (27) are now
  split into small functions in `command`: `removeSelection` with its checks and
  selectors, `initPrompt` with one function for each answer, and one function for
  each step of the upgrade. All three are under 16. `newWSTLSIssueRoles`,
  `newWSYardShare`, `NewCatalog`, `newWSTLSIssueUser` and `newWSTLSInstall`
  also have complexity at most 15. Their helpers separate prompts, validation,
  catalog rendering and certificate installation without changing their order.
  The last 17 (`NewAlias`, `NewProfiles`, `editUntilSaved`, `readProfileFile`,
  `NewContainers`, `NewImport`, `splitTraefikTLS`, `NewPublish`,
  `NewReachability`, `newWSLifecycle`, `newWSTLSIssueUser`,
  `newWSTLSIssueService`, `newWSSet`, `runYardInit`, `newWSYardPeers`,
  `runYardShare` and `readEncryptedKey`) are split too, and the Windows
  `validateOutputName` with them. No function of the code is over 15 or over 60
  lines.

## Quality tools

`mise run quality` runs all of them. `docs/development.md` has the details.

| Tool          | Checks             | Runs in `mise run check`                                                                                              |
| ------------- | ------------------ | --------------------------------------------------------------------------------------------------------------------- |
| golangci-lint | Go code            | Yes, the linters with no finding today, `gocyclo` and `funlen` among them. The rest are in `quality:go`               |
| SpotBugs, PMD | Java code          | Yes, SpotBugs with a skip list, and PMD for unused code, null-unsafe comparisons and locale-dependent case conversion |
| PIT           | Java unit tests    | No                                                                                                                    |
| ArchUnit      | Java package rules | Yes, as `ArchitectureTest`                                                                                            |

### Mutation tests of the CLI

`mise run quality` does not run them. Go has no standard tool, and two
community tools were tried on `internal/command`:

- **gremlins** does not work here. It reports 800 mutants killed and none
  alive. Applied by hand, 4 of 36 sampled mutants that it called killed
  survive, and string `+` mutants that cannot compile count as killed. The
  cause is probably the temporary copy of the module: tests read
  `../../../config`. This is not confirmed.
- **go-mutesting** works. It mutates the file in place and runs the package
  tests. On `ws_settings.go` it found 104 mutants and 29 survivors (score
  0.72) in 68 seconds. Some survivors are unchecked output text, such as
  `Saved .env. Apply the settings:`. The whole package would take about half an
  hour, so run it on one file at a time:
  `go install github.com/avito-tech/go-mutesting/cmd/go-mutesting@latest`, then
  `go-mutesting --exec-timeout=60 internal/command/<file>.go` from `cli/`.

On 2026-11-19, go-mutesting ran on the three files where a wrong answer costs
the most: the CA key code, the certificate checks, and `ws set`. The score is
the share of mutants that a test kills.

| File             | Mutants | Score at first | Score now |
| ---------------- | ------- | -------------- | --------- |
| `tlsca/key.go`   | 143     | 0.39           | 0.81      |
| `tlsca/tlsca.go` | 159     | 0.76           | 0.84      |
| `ws_settings.go` | 50      | 0.66           | 0.78      |

`ws_settings.go` is the file before `newWSSet` was split. The tests that raised
the scores:

- A CA key that OpenSSL encrypted opens with its password. A round trip through
  our own constants cannot tell that an algorithm ID is wrong: 57 of the
  surviving mutants of `key.go` changed an ID.
- `EncryptKey` writes the 600000 rounds that the comment names.
- A certificate that is not valid yet, or that has the CA flag without the
  signing usage, is refused. The expiry tests did not cover the start of the
  validity.
- `ws set` refuses the other names of `workstation`, and prints what to run next.

What survives is equal in effect or cannot happen. An error from `crypto/rand`
or from `asn1.Marshal` cannot be made to happen in a test, and the checks of the
padding byte in `decryptKey` give the same answer as the parse that follows
them: a wrong password fails there either way.
