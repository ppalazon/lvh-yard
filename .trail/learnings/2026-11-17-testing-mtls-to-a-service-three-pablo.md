---
title: "Testing mTLS to a Service: three traps"
author: Pablo
date: 2026-11-17
branch: main
id: l-3b2ed33e
scope: repo
status: active
src: agent
touches:
  - cli/internal/command/ws_tls_issue.go
---

## Watch out
- The image mendhak/http-https-echo serves a self-signed certificate from /app/fullchain.pem and /app/testpk.pem, and Traefik checks Services with mtls-ca.crt, so the route fails with unknown authority. Mount service-fullchain.crt and service.key from ws tls ca issue service over those two paths.
- With MTLS_ENABLE=1 that image asks for a client certificate and echoes it, but does not validate it. So it shows what Traefik sent. It does not show that a Service rejects an unknown client.
- A TLS passthrough host (tcp router with tls.passthrough) works beside a terminated host on the same entry point, chosen by SNI. Its Service certificate must name the hostname that the client uses, because Traefik does not terminate the connection: ca issue service needs --address for that name. Tried by hand only. LVH Yard builds no passthrough Route.
- Request the hostname through Traefik to prove the hop works. The Service's own log or response shows what Traefik sent.
