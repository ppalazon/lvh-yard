---
title: "Traefik's admin page and ping are on the entry point named traefik, on loopback"
author: Pablo
date: 2026-11-03
branch: core-services-01
cost: { minutes: 7, tokens: 28973 }
id: l-7aee7b36
scope: repo
status: active
src: agent
touches:
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/TraefikSettings.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/TraefikAdminTest.java
---

## Decided
- The spec asked for an entry point called admin with --api.insecure=false. Traefik serves its API without a Route only on the entry point named traefik (--api.insecure=true); with insecure=false the API needs a router to api@internal from the dashboard's Routes, so it would vanish while the dashboard is down. So the admin entry point is named traefik, bound to 127.0.0.1:${YARD_TRAEFIK_ADMIN_PORT:-8080}, with --api.insecure=true and --ping.entryPoint=traefik. "Insecure" here only means "no router": it is loopback-only.
- The traefik entry point must be declared: Traefik otherwise makes one itself on :8080, on every address.
- TraefikAdminTest runs Traefik from compose.yaml itself (image, entrypoint, command, Compose's ${VAR:-default} filled in), so it tests the real file, not a copy of its flags.

## Watch out
- Traefik adds its ping and API routers a moment after it logs "Starting provider *http.Provider". A request right after that gets 404, so the test waits until the admin port stops answering 404.
- Free ports taken one by one (ServerSocket(0), closed) can come back the same; take several together.
- dashboard/src/main/css/app.css is ignored by .gitignore's "app.css" line, so a fresh worktree has no CSS source and `mise run check` fails at the css step; copy it from the main checkout.
