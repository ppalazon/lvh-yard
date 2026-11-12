---
title: "Each Publication says whether Traefik logs its requests, and it is off by default"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-1f9ce2c1
scope: repo
status: active
replaces: l-1b1e7a1d
src: agent
touches:
  - config/compose.yaml
---

## Decided
- The user wanted the Traefik access log, but not the lines from LVH Yard's own pages, which poll every few seconds. Traefik's access log filters work only by status, duration or retries, so the choice is made per router.
- The setting is part of the Publication. The user asked for off by default, so a new Publication is not logged until someone turns it on. The first setup needs no special case.
- An Alias's Route follows the access log of the Service it names, as it follows its Route Profiles.

## Tried and ruled out
- A hidden rule that turns the log off for Routes whose Instance is this Workstation's dashboard, Consul or Traefik admin address. LVH Yard has no Core Services, and a hidden rule would surprise the user.
- On by default, with the first setup turning it off for yard, consul-ui and traefik-admin. The user preferred off for every Service.

In the code: Publication.accessLog, PublicationRequest.accessLog (null keeps), StoredPublication.accessLog (written only when true), TraefikConfiguration.Router.observability ({accessLogs: false} unless on), RouteBuilder.router, Backup.Published.accessLog (file key access-log, missing means false), lvh-yard publish --access-log on|off, config/compose.yaml --accesslog=true and config/00-lvh-yard.hcl log_level.
