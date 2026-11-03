# 02: Status of Traefik and the Core Services

**What to build:** The status adds Traefik (ping, last configuration fetch) and each Core Service's Health, so the user can tell a proxy problem from a Service problem.

**Blocked by:** 01, core-services/02

**Status:** ready-for-agent

- [ ] Traefik part: `/ping` on the admin port, and the time Traefik last fetched its configuration; older than 15s is a warning
- [ ] Core Services part: each one's Health; a missing one is failing
- [ ] Tested with a real Traefik, running and stopped
- [ ] `lvh-yard status` shows both parts
