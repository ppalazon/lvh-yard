# 02: CLI: `yard list`, `yard register`, `yard remove`

**What to build:** A developer lists the Services, registers a Static Instance and removes one from a terminal, with the `yard` command talking to the local dashboard's REST API. This also sets up the CLI test harness: a WireMock stub dashboard that answers only what `openapi.yaml` describes.

**Blocked by:** 01 (Register a Static Instance and see it in the Catalog)

**Status:** done

- [x] CLI tests run `yard` with arguments against a WireMock stub dashboard, and check output and exit code.
- [x] The stubs follow the committed `openapi.yaml`.
- [x] `yard list` prints each Service with its Instances and their Health.
- [x] `yard register` adds a Static Instance by Service name, host and port, with the host defaulting as in the API.
- [x] `yard remove` removes a Static Instance.
- [x] A refusal from the dashboard, such as an invalid name, is printed with its reason, and the exit code is non-zero.
- [x] When the dashboard does not answer, the CLI says that the dashboard is not running on this Workstation and how to start it, and the exit code is non-zero.
- [x] The dashboard URL comes from `--dashboard` or `YARD_DASHBOARD_URL`, with the loopback default.
