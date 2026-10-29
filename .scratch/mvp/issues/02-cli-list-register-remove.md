# 02: CLI: `yard list`, `yard register`, `yard remove`

**What to build:** A developer lists the Services, registers a Static Instance and removes one from a terminal, with the `yard` command talking to the local dashboard's REST API. This also sets up the CLI test harness: a WireMock stub dashboard that answers only what `openapi.yaml` describes.

**Blocked by:** 01 (Register a Static Instance and see it in the Catalog)

**Status:** ready-for-agent

- [ ] CLI tests run `yard` with arguments against a WireMock stub dashboard, and check output and exit code.
- [ ] The stubs follow the committed `openapi.yaml`.
- [ ] `yard list` prints each Service with its Instances and their Health.
- [ ] `yard register` adds a Static Instance by Service name, host and port, with the host defaulting as in the API.
- [ ] `yard remove` removes a Static Instance.
- [ ] A refusal from the dashboard, such as an invalid name, is printed with its reason, and the exit code is non-zero.
- [ ] When the dashboard does not answer, the CLI says that the dashboard is not running on this Workstation and how to start it, and the exit code is non-zero.
- [ ] The dashboard URL comes from `--dashboard` or `YARD_DASHBOARD_URL`, with the loopback default.
