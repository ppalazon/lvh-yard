---
status: accepted
---

# The CLI is written in Go

The `lvh-yard` executable that GraalVM builds is 49 MB, and the release needs GraalVM, a runner for each platform and Ubuntu 22.04 for its older glibc. The stripped Go executable measures 9.19 MB (9,187,488 bytes), and `--help` takes a median of 1.44 ms over 100 runs. It cross-compiles on one runner and needs no system library. So the whole CLI moves to Go and replaces the Java CLI. The command and option names stay the same, so users change nothing. The dashboard stays in Java. The two share only the API contract in `dashboard/openapi.yaml`.

## Considered Options

- Keep Java and build with `-Os`: rejected. It gives 40 MB, and the GraalVM release stays.
- Port only the commands that call the API: rejected. Without `init`, a user cannot set up a Workstation with the Go executable, so it cannot replace the old one.
- Keep both CLIs: rejected. Two CLIs need two binary names, two sets of docs and two test suites.
- Generate the client with `oapi-codegen`: rejected. It works with the OpenAPI 3.1 document, but it makes about 6,700 lines and adds three modules. A hand-made client is one struct per schema and one method per operation.
- Write the CLI without a command library: rejected. Cobra adds 0.6 MB and gives nested commands, usage errors and Bash and Zsh completion.

## Consequences

- The repo has two languages and two build tools. Maven builds the dashboard, and `go` builds the CLI.
- Only the names of commands and options are a contract. Help text, completion scripts and exit codes (0 or 1) follow the Go libraries.
- The Go client has no code generated from `openapi.yaml`. One test fails when the client calls a method and path that the document lacks. It cannot see a renamed JSON field.
- The dependencies are `net/http`, `encoding/json`, Cobra, and one YAML module for `profiles` only.
- `init` embeds `compose.yaml` and `.env.example` with `go:embed`. It cannot read files outside its module, so the CLI keeps committed copies, and a test fails when a copy differs from the file at the repo root.
- The release drops `cli-native`, GraalVM, `NativeRecords` and the second test run against the executable.
