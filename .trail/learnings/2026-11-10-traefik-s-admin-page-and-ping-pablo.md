---
title: "Traefik's admin page and ping are on the entry point named traefik, on loopback"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 7, tokens: 28973 }
id: l-a62354ae
scope: repo
status: active
replaces: l-7aee7b36
src: agent
touches:
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/TraefikSettings.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/TraefikAdminIT.java
---

## Decided
- Traefik serves its API without a Route only on the entry point named traefik, with --api.insecure=true. With insecure=false, the API needs a router from the dashboard's Routes, so it vanishes while the dashboard is down. So the admin entry point is named traefik, bound to 127.0.0.1:${YARD_TRAEFIK_ADMIN_PORT:-8080}, with --ping.entryPoint=traefik. "Insecure" only means "no router" here, and it is loopback only.
- The traefik entry point must be declared, because otherwise Traefik makes one itself on :8080 on every address.
- TraefikAdminIT runs Traefik from compose.yaml itself, so it tests the real file, not a copy of its flags.

## Watch out
- Traefik adds its ping and API routers a moment after it logs "Starting provider *http.Provider". So the test waits until the admin port stops answering 404.
- Free ports taken one by one can come back the same. Take several together.

In the code: compose.yaml (traefik command), TraefikAdminIT.java.
