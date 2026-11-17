---
title: ws tls ca export is gone and only ca issue service makes a zip
author: Pablo
date: 2026-11-17
branch: main
id: l-a3ca140a
scope: repo
status: active
src: agent
touches:
  - cli/internal/command/ws_tls_issue.go
  - cli/internal/command/ws_tls.go
  - cli/internal/command/ws_yard_share.go
---

## Decision
- ws tls ca export (user and ca) was removed. It only copied a .p12 file and ca.crt into an unencrypted zip, and a person can send those two files as they are.
- ca issue service keeps a zip because a Service needs the key, the full chain and ca.crt together, and the key travels, so the zip is encrypted with AES-256 and its password is shown once. ws yard share does the same for the Consul certificate.
- ca issue user makes no zip and no README. The import steps for a browser are in the user guide only.
- Considered and not built: packing a service zip again later from an existing certificate.
