# The lvh-yard CLI in Go

Status: ready-for-agent

Decision: ADR-0010. Tickets: `issues/02` to `issues/17`.

This work happens on the branch `go-cli`, in its own worktree. It merges once, at the cutover.

## Problem Statement

The `lvh-yard` executable is 49 MB. Users download it for every release, and for each platform. The release is also heavy for the maintainer: it needs GraalVM, one runner for each platform, an older Ubuntu for its glibc, and a second run of the CLI tests against the executable. A small command line tool should not cost this much to ship or to build.

## Solution

Write the whole CLI again in Go. The user sees the same commands and options, so nothing changes in how they use LVH Yard. The executable is about 5 MB, starts in about a millisecond, and has no system library. One cheap runner builds every platform. The Java CLI is deleted when the Go CLI does everything that it did.

## User Stories

1. As a Workstation owner, I want a small `lvh-yard` download, so that installing it on every Workstation is quick
2. As a Workstation owner, I want the same commands and options as before, so that my notes and shell history still work
3. As a Workstation owner, I want `lvh-yard --version` and `--help`, so that I can see what I have and what it does
4. As a Workstation owner, I want `lvh-yard init` to ask only for the Workstation name and the Base Domain at a terminal, so that setup is short
5. As a Workstation owner, I want `lvh-yard init --workstation` and `--base-domain` to need no terminal, so that I can set up a Workstation from a script
6. As a Workstation owner, I want `init` to refuse to overwrite my files unless I give `--force`, so that I lose no hand edits
7. As a Workstation owner, I want `init --upgrade` to change only the image lines of consul, traefik and dashboard, so that my other edits stay as they are
8. As a Workstation owner, I want `init --upgrade` to change nothing when an image line is missing, so that I never get a half-upgraded file
9. As a Workstation owner, I want `init --reinit` to keep my old settings and take new ones from flags, so that I can join a Yard later with `--peers` and `--gossip-key`
10. As a Workstation owner, I want `init --reinit` to keep `.bak` copies of the old files, and only after every flag is checked, so that a usage error changes nothing
11. As a Workstation owner, I want `init` to find my Docker group by itself, so that the dashboard can reach Docker
12. As a Workstation owner, I want the Workstation Folder to pin the dashboard image to the version of my CLI, so that the two match
13. As a developer, I want a build from the sources to use the `latest` image, so that I can try the CLI before a release
14. As a Workstation owner, I want the CLI to find my dashboard from `--dashboard`, then `YARD_DASHBOARD_URL`, then the `.env` of `init`, then `127.0.0.1:7780`, so that it works with no setup after `init`
15. As a Workstation owner, I want a clear message when the dashboard does not answer, with the address that the CLI tried, so that I know what to fix
16. As a Workstation owner, I want `lvh-yard status` to show the Status of each Status Part and the worst one, so that I see if LVH Yard itself works
17. As a Workstation owner, I want `lvh-yard catalog` to list the Services of every Workstation, one line each, so that I see the Yard at a glance
18. As a Workstation owner, I want `catalog --workstation`, `--state` and `--details`, so that I can narrow the list and see Instance addresses, Health, IDs and Service Warnings
19. As a Workstation owner, I want `lvh-yard service create` and `delete` to be safe to repeat, so that scripts do not fail on the second run
20. As a Workstation owner, I want `lvh-yard register static` to add a Static Instance, so that I can reach a process that is not in a container
21. As a Workstation owner, I want `lvh-yard register container` and `register compose` to make a Container Binding, so that the Bound Instances follow my containers
22. As a Workstation owner, I want several Services to bind the same container or Compose service, so that I can give one container more than one hostname
23. As a Workstation owner, I want `lvh-yard containers` to list my containers and mark each Unfollowed Container, so that I see what no Service follows yet
24. As a Workstation owner, I want `lvh-yard remove` to remove one Instance Source, so that I can clean up a Service without deleting it
25. As a Workstation owner, I want `lvh-yard publish` to give a Service a Publication with a Scope, so that it gets a hostname
26. As a Workstation owner, I want to choose whether Traefik logs the requests of a Publication, so that I can debug one Route
27. As a Workstation owner, I want `lvh-yard unpublish` to remove only the Publication, so that the Service and its Instances stay
28. As a Workstation owner, I want `lvh-yard alias add`, `list` and `remove`, so that a Service can use the Instances of a Remote Service
29. As a Workstation owner, I want a clear refusal when I change a Remote Service, so that I know only its Home Workstation can do it
30. As a Workstation owner, I want `lvh-yard priority` to set the Instance Priority of a Service, so that its Route uses the first Instance Source that has a passing Instance
31. As a Workstation owner, I want `lvh-yard test` to show Reachability of an Instance, directly and through its Route, so that I can find where a connection breaks
32. As a Workstation owner, I want `lvh-yard profiles`, `show`, `set`, `edit` and `remove`, so that I manage Route Profiles from the shell
33. As a Workstation owner, I want Route Profiles in Traefik's file format, so that I can copy examples from Traefik's documentation
34. As a Workstation owner, I want `profiles show NAME | profiles set -` to give back the same profile, including a string like `"100"`, so that a round trip does not change my data
35. As a Workstation owner, I want `profiles edit` to open `$EDITOR`, even when it holds arguments such as `code --wait`, so that I can use my usual editor
36. As a Workstation owner, I want a refused `profiles set` to show one line for each profile that is wrong, so that I can fix them all at once
37. As a Workstation owner, I want `lvh-yard export` to print a Backup as YAML, and `-o` to write it to a file that only I can read, so that my setup is safe
38. As a Workstation owner, I want `lvh-yard import` to add what is new in a Backup and skip what exists, so that I can restore or copy a setup
39. As a Workstation owner, I want `import` to show its plan before it changes anything, when I ask for a dry run, so that I can check it
40. As a shell user, I want `lvh-yard completions bash` and `zsh`, so that Tab completes commands, options and values
41. As a script author, I want exit code 0 on success and 1 on any error, so that my script can test one thing
42. As a script author, I want errors on standard error and results on standard output, so that I can pipe results
43. As a maintainer, I want one test seam at the command line and one at the client, so that tests are few and each one shows a behavior
44. As a maintainer, I want a test that fails when the client calls a method and path that `openapi.yaml` lacks, so that a renamed endpoint does not reach users
45. As a maintainer, I want one file of templates to be the source of `init`, so that `compose.yaml` and `.env.example` cannot drift from what `init` writes
46. As a maintainer, I want a release that cross-compiles on one runner, so that it needs no GraalVM and no runner for each platform
47. As a maintainer, I want the Go work on its own branch until it is finished, so that `main` keeps working for users and for the Java CLI
48. As a maintainer, I want a measure of size and start time after the first commands, so that I can stop cheaply if the gain is small
49. As a maintainer, I want the cutover to delete the Java CLI and its GraalVM code in one merge, so that the repo has one CLI and no dead code
50. As a maintainer, I want the docs, the learnings and the ADR to match after the cutover, so that nobody reads about GraalVM and picocli by mistake

## Implementation Decisions

- **Replace, not add.** The Go CLI replaces the Java CLI. There is one `lvh-yard`, with no shim and no second binary name. The dashboard stays in Java. The two share only the API contract in `openapi.yaml`.
- **Contract.** Only the names of commands and options match the Java CLI. Help text, completion scripts and exit codes follow the libraries. Exit code 0 is success and 1 is any error, usage errors included.
- **Where the CLI looks for the dashboard.** The order is `--dashboard`, `YARD_DASHBOARD_URL`, the `.env` from `init` (found through `YARD_CONFIG_DIR`, `$XDG_CONFIG_HOME/lvh-yard` or `~/.config/lvh-yard`), then `http://127.0.0.1:7780`. A bind address of `0.0.0.0` in the `.env` becomes `127.0.0.1`. A missing or unreadable `.env` is not an error.
- **Modules.**
  - A **client** module for the dashboard's REST API: one type for each schema of `openapi.yaml` and one method for each operation. It is written by hand with `net/http` and `encoding/json`, with no generated code. It owns the timeouts (connect 2 seconds, answer 10 seconds) and turns every failure into a message that is ready to print. It reads the Backup as YAML text and sends it as YAML text, without parsing it.
  - A **command** module with one group of commands for each area of the Java CLI. It prints results and errors, and it knows nothing about HTTP.
  - A **process** interface for what the CLI takes from its process: environment, standard input, terminal check, `$EDITOR` and `id`. The real one is used in production, and tests give their own.
  - A **templates** module that embeds copies of `compose.yaml` and `.env.example`, and a version value that sets the dashboard image tag.
- **Dependencies.** The standard `net/http` and `encoding/json`, Cobra for commands, and one YAML module used for Route Profiles only. `export` and `import` pass YAML through as text. `init` edits `compose.yaml` and `.env` as text.
- **Commands.** `init` (with `--force`, `--upgrade` and `--reinit`, which exclude each other), `catalog`, `service` (`create`, `delete`), `register` (`static`, `container`, `compose`), `containers`, `remove`, `publish`, `unpublish`, `priority`, `alias` (`add`, `list`, `remove`), `profiles` (`show`, `set`, `edit`, `remove`), `export`, `import`, `test`, `status`, and a hidden `completions`. Each keeps its options.
- **Templates for `init`.** `go:embed` cannot read outside its module. The CLI keeps committed copies of the two files, and a task refreshes them from the repo root.
- **Version.** The release job sets the version when it builds. A build without it reports `dev` and writes the `latest` dashboard image tag. A release build writes its own version.
- **Build.** The executable has no C code, so it needs no system library. Builds are stripped.
- **Release.** One job on an ordinary runner cross-compiles `linux-amd64`, `linux-arm64` and `macos-arm64`. Each is packed as a `tar.gz` with the licence, and `SHA256SUMS` lists them, as now.
- **Order of work.** First the skeleton with `status` and `catalog`, then a measure of size and start time. Then read commands, write commands, `profiles` with `export` and `import`, `init`, and `completions`. The cutover is last. Each step ends with working tests.
- **Cutover.** One merge deletes the Java CLI, its Maven module, the native build tasks and the native release job. It renames the Go folder to the CLI's place, updates the docs, and replaces the learnings that the port makes stale.

## Testing Decisions

- **A good test** runs a command or a client call as a user would, and checks what the user can see: the exit code, the text printed, and the requests that the dashboard got. It does not check the structure of the code, the help layout, or exact sentences that no user depends on.
- **Seams.**
  - The **command line**: a test runs the root command in process, with arguments, environment, standard input and a fake process, against a dashboard that the test writes by hand. This is the main seam. Every command, every option and the exit code are tested here.
  - The **client**: a test calls a client method against its own fake server. It checks the request that the method builds (the method, the path with its escaped segments, the headers and the body) and how it handles each answer: success, a problem answer with its reasons, a refused connection, a timeout and an answer that is not JSON. The check that the client calls only methods and paths of `openapi.yaml` is here. It reads the document as plain YAML, because the document has no `info.version`, which a strict validator refuses.
- **Smoke test.** One test runs the built executable against a hand-written fake dashboard. It shows that `main`, the version and the exit code work. By ADR-0008 it is an integration test, because it uses another process.
- **A drift test** compares each committed template copy with its file at the repo root.
- **Prior art.** Port the cases of the Java tests that test CLI behavior and skip the cases that test the dashboard. The Java tests are `YardTest`, `CatalogTest`, `StatusTest`, `ServiceCommandTest`, `ContainersTest`, `TestReachabilityTest`, `RegisterTest`, `PublishTest`, `AliasTest`, `ProfilesTest`, `BackupTest`, `InitTest` and `YardProcessIT`. Keep the edge cases that the learnings record: the `.env` address lookup, the string `"100"` in a round trip, the input that ends early, and `.bak` files that are written only after every flag check.
- **Not tested.** The dashboard's API itself. `OpenApiComponentTest` in the dashboard already keeps `openapi.yaml` equal to it. A renamed JSON field is not caught without a real dashboard, so check each command by hand against `mise run dev:up`.

## Out of Scope

- A change to the dashboard or to `openapi.yaml`.
- A new command, a new option or a new output format.
- More platforms than the three above. Another one later is one line in the release job.
- Windows support.
- Generating the client from `openapi.yaml`.
- Podman.

## Further Notes

- The size gain is an estimate from a small test program: 4.5 MB with `net/http`, `encoding/json` and `flag`, 5.1 MB with Cobra, and 5.2 MB with the YAML module. The first ticket measures the real executable and stops the work if the gain is small.
- Write all text in simple English, as `CLAUDE.md` says. CLI text names `docker compose` and `lvh-yard` commands, and never `mise` tasks.
- `GLOSSARY.md` does not change. The port adds no domain term.
