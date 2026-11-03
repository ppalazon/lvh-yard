# 01: Routes prefer Static, then Bound Instances, by Health

**What to build:** With `web` running in Docker and in a terminal, `web.lvh.me` reaches only the terminal copy while its check passes, and the Docker copy when it stops. See `.scratch/instance-priority/spec.md` and ADR-0005.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] The Route builder drops Out-of-range Instances, then uses the first kind of `[static, bound]` with a passing Instance, routing only its passing Instances
- [x] With no passing Instance in any kind, the Route gets every in-range Instance
- [x] The same choice is made on every Workstation for Yard-scoped Services
- [x] The API's Service model has `priority` and `routing` (kind in use, Instance IDs), in `openapi.yaml`
- [x] The Service page shows `routing to: <kind> (<others> waiting)`; `lvh-yard list` marks routed Instances
- [x] An end-to-end test with real Traefik: the hostname moves from the Static to the Bound Instance when the Static one stops, and back
