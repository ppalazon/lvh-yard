---
title: "A Reachability test skips Out-of-range Instances, sends its Traefik request by hand, and its tests start a real Traefik once the dashboard's port is known"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 14, tokens: 52195 }
id: l-e23359a1
scope: repo
status: active
replaces: l-c2375316
src: agent
touches:
  - ".scratch/mvp/issues/11-test-reachability.md"
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
- An Out-of-range Instance is skipped, not failed. The user asked for this: the address alone already says only its Home Workstation can connect, and a connection from here would reach this computer instead. The result is outOfRange: true, reached: false, no error, and it does not make passed false. The user's word was "out of scope", but Scope means Local or Yard Scope here, so it is called out of range.
- The request through Traefik must carry the hostname in its Host header. The JDK HTTP client and HttpURLConnection refuse to set Host, so it is written by hand over a Socket. Reading stops after the headers, so a streaming Service is not waited on.
- A Route check fails only on Traefik's own 404 (body exactly "404 page not found") or on 502, 503 or 504. Any other status, even an app's 404 or 500, reached the Service.

## Watch out
- Traefik needs the dashboard's random port, and the dashboard needs Traefik's port before it starts. The Traefik port is chosen first and given as a property, and Traefik starts in the test class once the dashboard's port is known.
- The Consul image's nc serves one connection at a time, so checks racing on it flake. The tests use the JDK's HttpServer on loopback as the Instance.
- The API leaves false booleans out (NON_DEFAULT). The CLI's Jackson 3 refuses a missing primitive, so the CLI reads such a field as Boolean.

In the code:
- reachability/ReachabilityTester (connect, request, read); InstanceReachability.failed(); setting lvh-yard.traefik.url (YARD_HTTP_PORT)
- cli TestReachability and Dashboard.InstanceReachability.skipped()
- TestcontainersConfiguration.TRAEFIK_PORT and traefik(dashboardPort); ReachabilityTest.startTraefik
