# 02: Go tooling and a root command

Status: ready-for-agent

Blocked by: None (can start immediately)

Spec: `../spec.md`. Work in the worktree `../lvh-yard-go`, on the branch `go-cli`. The Go code goes in a new folder next to the Java `cli/`, which stays as it is.

## What to build

An executable named `lvh-yard` that prints its version and its help, and that knows where the dashboard is. It has no real command yet.

- The root command, built with Cobra, with `--version`, `--help` and `--dashboard <url>`.
- The dashboard address lookup, in this order: `--dashboard`, `YARD_DASHBOARD_URL`, the `.env` that `init` wrote (found through `YARD_CONFIG_DIR`, `$XDG_CONFIG_HOME/lvh-yard` or `~/.config/lvh-yard`), then `http://127.0.0.1:7780`. A bind address of `0.0.0.0` in the `.env` becomes `127.0.0.1`. A missing or unreadable `.env` is not an error. A missing setting falls back to the same defaults as `compose.yaml`. Follow `Yard.java`, `ConfigFolder.java` and `EnvFile.java`.
- The version value: a build without a version reports `dev`. A build with one reports it.
- An error is printed on standard error and the exit code is 1. A usage error has exit code 1 too.
- The command-line test helper (the main seam of the spec): it runs the root command in process with arguments, environment, standard input and a fake process (terminal check, `$EDITOR`, `id`), and gives back standard output, standard error and the exit code. Later tickets reuse it.
- `mise.toml` pins `go`, and `mise run cli:build` builds a stripped executable with no C code. `mise run check` and `mise run test:unit` also run `go vet` and `go test ./...`, and the sources of `check` list the Go folder. A `gofmt` hook is added to the pre-commit configuration.
- Port the cases of `YardTest` that test the address lookup and the version.

## Acceptance criteria

- [ ] `lvh-yard --version` prints the version, and `dev` for a plain build.
- [ ] The address lookup tests cover each of the four sources, their order, the `0.0.0.0` mapping and a missing `.env`.
- [ ] `mise run cli:build` makes the executable.
- [ ] `mise run check` runs the Go tests and passes.
- [ ] The `gofmt` hook fails on a badly formatted Go file.
