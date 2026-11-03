# 01: Status of Consul and Docker in the API, a page and the CLI

**What to build:** The user opens `/status`, or runs `lvh-yard status`, and sees whether Consul and Docker work on this Workstation. See `.scratch/status/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `GET /api/status` returns `overall` and `parts` (`name`, `state` ok|warning|failing, `reason`, `details`), in `openapi.yaml`
- [ ] Consul part: answers, leader, WAN members and their state; a configured peer missing from the WAN pool is a problem
- [ ] Docker part: socket reachable and version; a missing socket and a permission problem are told apart
- [ ] Parts are read in parallel with short timeouts and cached about 5s; a slow part never blocks the page
- [ ] `/status` page refreshes by itself
- [ ] `lvh-yard status` prints each part; exit 0 when all OK, 1 otherwise; warnings fail only with `--strict`
- [ ] Tests: two-datacenter Yard for the WAN pool; a dashboard pointed at a missing socket
