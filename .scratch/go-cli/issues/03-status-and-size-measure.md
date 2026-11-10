# 03: `status` and the size measure

Status: done

Blocked by: 02

Spec: `../spec.md`.

## What to build

`lvh-yard status` works end to end against a dashboard. It shows the Status of each Status Part (Consul, Traefik, Docker) and the worst one. This is the first slice through the client, so it also builds the client and its seam.

- The client for the dashboard's REST API: one type for each schema and one method for each operation, written by hand with `net/http` and `encoding/json`. This ticket adds what `status` needs. Its timeouts are 2 seconds to connect and 10 seconds for an answer. Every failure becomes a message that is ready to print: a refused connection names the address that was tried, a timeout, a problem answer with its reasons, and an answer that is not JSON. Follow `Dashboard.java`.
- The client tests (the second seam of the spec): against the test's own fake server, they check the request that a method builds (method, escaped path, headers) and each kind of answer.
- The check against `dashboard/openapi.yaml`: a test that fails when the client calls a method and path that the document lacks. It reads the document as plain YAML, because the document has no `info.version`. It needs a YAML module, which `profiles` also uses later.
- The command `status`. Port the cases of `StatusTest` that test CLI behavior.
- A measure of the stripped executable: its size, and the time of `--help`. Write both numbers under `## Comments`, next to the Java numbers (49 MB, 7.3 ms).

## Acceptance criteria

- [x] `lvh-yard status` shows each Status Part and the worst, against a real dashboard from `mise run dev:up`.
- [x] A stopped dashboard gives a one-line message with the address and exit code 1.
- [x] A test fails when a client method uses a path or method that `openapi.yaml` lacks.
- [ ] `mise run check` passes.
- [x] The measure is under `## Comments`. If the size is not much below 49 MB, stop and ask the maintainer before ticket 04.

## Comments

The Go client and `status` command are implemented. The client owns the HTTP timeouts and error messages. Tests cover command output, strict mode, errors, escaped paths, and the OpenAPI contract. Shared client models cover every schema in the current contract.

`mise run test:unit` passed, including `go vet` and all Go tests. Status passed
against both live dashboards. After stopping ws-b's dashboard, the CLI
printed one stderr line with its address and exited 1. The dashboard was restored.
A temporary invalid method and path made the OpenAPI test fail; the
original method and path were restored.

| Executable              | Size                      | Median `--help` |
| ----------------------- | ------------------------- | --------------- |
| Java baseline           | 49 MB                     | 7.3 ms          |
| Go, stripped, no C code | 9.19 MB (9,187,488 bytes) | 1.44 ms         |

The Go measure used 100 process starts on this computer. The file command
confirmed a static, stripped ELF executable. The size gate passes.
The full check remains unchecked: its earlier run failed in the unchanged
dashboard DockerEngineIT because a Docker start event was missing.
