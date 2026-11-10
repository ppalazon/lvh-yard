---
title: "A Reachability test skips Out-of-range Instances, sends its Traefik request by hand, and its tests start a real Traefik once the dashboard's port is known"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-8878c3cc
scope: repo
status: active
replaces: l-4d2c2dfc
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/InstanceReachability.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/Reachability.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/ReachabilityTester.java
  - cli/internal/command/reachability.go
  - dashboard/openapi.yaml
---

## Decided
- An Out-of-range Instance is skipped, not failed, because the address alone already says that only its Home Workstation can connect, and a connection from here would reach this computer instead. The result is outOfRange: true, reached: false, and passed stays true.
- The request through Traefik carries the hostname in its Host header. The JDK HTTP client refuses to set Host, so the request is written by hand over a Socket. Reading stops after the headers, so a streaming Service is not waited on.
- A Route check fails only on Traefik's own 404 (body exactly "404 page not found") or on 502, 503 or 504. Any other status, even an app's 404 or 500, reached the Service.

## Watch out
- Traefik needs the dashboard's random port, and the dashboard needs Traefik's port before it starts. So the Traefik port is chosen first and given as a property, and Traefik starts once the dashboard's port is known.

In the code: reachability/ReachabilityTester.java, cli/internal/command/reachability.go, ReachabilityIT, TestcontainersConfiguration.
