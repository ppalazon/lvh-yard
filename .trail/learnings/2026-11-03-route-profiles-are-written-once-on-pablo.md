---
title: Route Profiles are written once on their Home Workstation and reach every other Workstation through Consul KV
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 1, tokens: 3694 }
id: l-69c6d2c9
scope: repo
status: active
src: agent
touches:
  - GLOSSARY.md
---

## Decided
- A Route Profile is a Traefik middleware written in route-profiles.yaml on its Home Workstation. The dashboard copies valid profiles into its own Consul KV; every Workstation routing the Publication (Aliases included) reads them there and serves them to Traefik through the HTTP provider as <workstation>-<profile>. No file provider, no file sync.
- Profiles that cannot work on another Workstation are refused: basicAuth.usersFile, forwardAuth to an out-of-range address, plugins. Chains may only name middlewares from the same file.
- A broken file keeps the last good profiles. A missing profile fails closed.
- Every auth profile carries a warning: a direct ip:port connection skips Traefik, so the Service must check users itself.

## Tried and ruled out
- Profiles applied only by the Home Workstation (Traefik file provider): the user rejected it, as other Workstations and their Aliases would route the Service without its auth.
- Each Workstation using its own file's profile of the same name, or syncing files with git/Syncthing/a pull command: files drift.

## Watch out
- Basic-auth hashes in KV are readable by the whole Yard. Vault is a later study (.scratch/vault-secrets/).

In the code: docs/adr/0004-route-profiles-travel-through-consul-kv.md, GLOSSARY.md (Route Profile, Instance Priority, Core Service).
