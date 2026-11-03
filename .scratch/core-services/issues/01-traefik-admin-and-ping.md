# 01: Traefik's admin page and ping on a loopback entrypoint

**What to build:** Traefik's own admin page and its ping answer on `127.0.0.1:8080` (or `YARD_TRAEFIK_ADMIN_PORT`), never on the LAN, so LVH Yard can check Traefik and the user can see its configuration. See `.scratch/core-services/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `compose.yaml` adds an `admin` entrypoint on `127.0.0.1:${YARD_TRAEFIK_ADMIN_PORT:-8080}` with the API dashboard and `/ping` served there only
- [ ] `.env.example` documents `YARD_TRAEFIK_ADMIN_PORT`, and the dashboard reads it
- [ ] The `web` entrypoint does not serve the API or ping
- [ ] The dev Yard (`mise run dev:up`) gives ws-a and ws-b different admin ports
- [ ] A test with a real Traefik reaches `/ping` on the admin port
