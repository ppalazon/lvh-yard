---
title: "A Reachability test sends its Traefik request by hand, and the tests start a real Traefik only once the dashboard's port is known"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 13, tokens: 211072 }
id: l-c2375316
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ReachabilityTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/ReachabilityTester.java
  - compose.yaml
---

## Decided
- The request through Traefik must name the Service's hostname in its Host header. The JDK HTTP client refuses to set Host (a restricted header), and so does HttpURLConnection, so the request is a few lines of HTTP/1.1 written over a plain Socket.
- A Route check fails only when Traefik has no Route (a 404 whose body is exactly "404 page not found", Traefik's own) or answers 502, 503 or 504. Any other status, even 404 or 500 from the app, means the request reached the Service.
- An Out-of-range Instance is not connected to and counts as failed: a connection would reach this computer's own loopback or containers.
- Hostnames tested: the Service's own hostname here, plus each active Alias that names it.
- The tests run Traefik in a container on the host network, as compose.yaml does, so it reaches the dashboard and Instances on 127.0.0.1.

## Watch out
- Traefik needs the dashboard's random port, and the dashboard needs Traefik's port before it starts. So the Traefik port is picked up front and handed to the dashboard as a property, and Traefik itself starts in the test class once the dashboard's port is known.
- The Consul image's nc serves one connection at a time, so a direct check and a Traefik request racing on it flake. The tests use the JDK's HttpServer on loopback as the Instance instead.

In the code:
- dashboard reachability/ReachabilityTester.request; setting lvh-yard.traefik.url (YARD_HTTP_PORT)
- TestcontainersConfiguration.TRAEFIK_PORT and traefik(dashboardPort); ReachabilityTest.startTraefik and answerHttpOnLoopback
