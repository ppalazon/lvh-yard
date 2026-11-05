---
title: "Route Profiles live only in their Home Workstation's Consul KV and every Workstation applies them"
author: Pablo
date: 2026-11-05
branch: main
cost: { minutes: 2, tokens: 6022 }
id: l-063d2a2d
scope: repo
status: active
replaces: l-69c6d2c9
src: agent
touches:
  - GLOSSARY.md
  - docs/adr/0004-route-profiles-travel-through-consul-kv.md
---

## Decided
A Route Profile is a Traefik middleware kept only in its Home Workstation's Consul KV. Every Workstation that routes a Publication naming it reads it there and applies it, Aliases included. The user dropped route-profiles.yaml: only the dashboard reads Consul KV, so the file was a second copy, and a Backup import could not write it.

## Tried and ruled out
- Profiles applied only by the Home Workstation (Traefik file provider): other Workstations and their Aliases would route the Service without its auth.
- route-profiles.yaml as the source that the dashboard polls: see the Backup learning.

## Watch out
Basic-auth hashes in KV are readable by the whole Yard. Vault is a later study (.scratch/vault-secrets).

In the code:
- docs/adr/0004-route-profiles-travel-through-consul-kv.md
- KV key: lvh-yard/route-profiles/<name>
