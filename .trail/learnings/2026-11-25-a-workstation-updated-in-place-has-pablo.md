---
title: A Workstation updated in place has no mTLS or client-auth setting in the dashboard
author: Pablo
date: 2026-11-25
branch: main
id: l-c2779367
scope: repo
status: active
replaces: l-38c70a15
src: agent
touches:
  - config/compose.yaml
  - cli/internal/command/init_upgrade.go
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/TraefikSettings.java
---

## Watch out
- The dashboard reads YARD_CLIENT_AUTH_ENABLED and YARD_MTLS_ENABLED from its own container environment, set in compose.yaml. The CLI writes them to .env too, but a compose.yaml from before the three TLS parts does not pass them on.
- ws compose update changes images only, so on such a folder the dashboard keeps both settings off. A Route Profile with clientCertificate then gets the no-client-authentication Service Warning, and one with mutualTLS gets no Route, even when ws tls client create or ws tls mtls create worked and Traefik loaded its files.
- Not fixed yet. A migration that adds the two lines to the dashboard service in compose.yaml and compose.windows.yaml would fix it. Until then, copy the two lines by hand from config/compose.yaml.
