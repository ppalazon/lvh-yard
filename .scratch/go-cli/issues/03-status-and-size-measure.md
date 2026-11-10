# 03: `status` and the size measure

Status: ready-for-agent

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

- [ ] `lvh-yard status` shows each Status Part and the worst, against a real dashboard from `mise run dev:up`.
- [ ] A stopped dashboard gives a one-line message with the address and exit code 1.
- [ ] A test fails when a client method uses a path or method that `openapi.yaml` lacks.
- [ ] `mise run check` passes.
- [ ] The measure is under `## Comments`. If the size is not much below 49 MB, stop and ask the maintainer before ticket 04.
