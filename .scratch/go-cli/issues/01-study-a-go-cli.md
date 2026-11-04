# Study: write the lvh-yard CLI in Go

Status: needs-triage

The native `lvh-yard` executable is 49 MB, or 16 MB as tar.gz, with -O3. A Go CLI of this kind is usually 8 to 12 MB, or about 4 MB as tar.gz. Study whether a port to Go is worth its cost.

A Go build can also make the release simpler. Go cross-compiles, which means that one computer builds for every OS and CPU. With `CGO_ENABLED=0`, the executable needs no system libraries. Then the release needs no GraalVM, no runner per platform, no Ubuntu 22.04 for its older glibc, no NativeRecords Feature and no second test run against the executable.

The cost is a port of about 2,600 lines of CLI code and 2,900 lines of tests. The repo then has two languages and two build tools. The CLI and the dashboard then share only the API contract in `dashboard/openapi.yaml`.

Questions to answer:

- How large is a Go `lvh-yard` and how fast does it start? Port only `list` and `status` against the real API, and measure.
- Can `oapi-codegen` generate the API client from `dashboard/openapi.yaml`, and does the client stay readable?
- Can `go:embed` hold the `init` templates, which the Maven build now copies from `compose.yaml` and `.env.example`?
- Which test tool replaces WireMock, and can the current tests serve as the checklist for the port?
- How do `mise run check`, the pre-commit hook and the release workflow build and test Go?
- What is the order of the port: one command at a time, or all at once?

If the answer is yes, record the decision in an ADR, then write the spec with the spec skill.
