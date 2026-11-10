---
title: "A Reachability test skips Out-of-range Instances, sends its Traefik request by hand, and its tests start a real Traefik once the dashboard's port is known"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 14, tokens: 52195 }
id: l-4d2c2dfc
scope: repo
status: active
replaces: l-e23359a1
src: agent
touches:
  - README.md
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/TestReachability.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/InstanceReachability.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/Reachability.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/ReachabilityTester.java
---

## Decided
- An Out-of-range Instance is skipped, not failed, because the address alone already says that only its Home Workstation can connect, and a connection from here would reach this computer instead. The result is outOfRange: true, reached: false, and passed stays true.
- The request through Traefik carries the hostname in its Host header. The JDK HTTP client refuses to set Host, so the request is written by hand over a Socket. Reading stops after the headers, so a streaming Service is not waited on.
- A Route check fails only on Traefik's own 404 (body exactly "404 page not found") or on 502, 503 or 504. Any other status, even an app's 404 or 500, reached the Service.

## Watch out
- Traefik needs the dashboard's random port, and the dashboard needs Traefik's port before it starts. So the Traefik port is chosen first and given as a property, and Traefik starts once the dashboard's port is known.
- The API leaves false booleans out (NON_DEFAULT), so the CLI reads such a field as Boolean.

In the code: reachability/ReachabilityTester.java, cli TestReachability.java, ReachabilityIT, TestcontainersConfiguration.
