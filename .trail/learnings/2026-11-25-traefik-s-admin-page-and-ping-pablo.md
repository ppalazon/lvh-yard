---
title: "Traefik's admin page and ping are on the entry point named traefik, on loopback, default port 7781"
author: Pablo
date: 2026-11-25
branch: main
cost: { minutes: 5 }
id: l-74cdc423
scope: repo
status: active
replaces: l-ae698b53
src: agent
touches:
  - config/compose.yaml
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/TraefikSettings.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/TraefikAdminIT.java
---

## Decided
- Traefik serves its API without a Route only on the entry point named traefik, with --api.insecure=true. With insecure=false, the API needs a router from the dashboard's Routes, so it vanishes while the dashboard is down. So the admin entry point is named traefik, bound to 127.0.0.1:${YARD_TRAEFIK_ADMIN_PORT:-7781}, with --ping.entryPoint=traefik. "Insecure" only means "no router" here, and it is loopback only.
- The traefik entry point must be declared, because otherwise Traefik makes one itself on :8080 on every address. That is Traefik's own built-in default, not ours.
- The default admin port is 7781, not 8080, because 8080 is the usual port of Java development servers and clashed with the developer's own. 7781 sits next to the dashboard's 7780, so the two are easy to remember together. It is below the ephemeral range (32768 and up), so the OS never hands it to another program. A random port was rejected because the docs and users would have to remember it.
- Workstations made before the change keep YARD_TRAEFIK_ADMIN_PORT=8080 in their .env. Only new ws init Workstations get 7781.
- TraefikAdminIT runs Traefik from config/compose.yaml itself, so it tests the real file, not a copy of its flags.

## Watch out
- Traefik adds its ping and API routers a moment after it logs "Starting provider *http.Provider". So the test waits until the admin port stops answering 404.
- Free ports taken one by one can come back the same. Take several together.

In the code: config/compose.yaml (traefik command), TraefikSettings.java (the admin URL), TraefikAdminIT.java.
