---
title: "The unpublished-port warning comes from a flag the Bound Instance stores, not from its address, and a host-network container never gets it"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 9, tokens: 113771 }
id: l-14222377
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainerBindingsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
  - GLOSSARY.md
---

## Decided
- When a Container Binding registers a Bound Instance, it records whether the Instance is on a port of the host. That is true for a published container port, and also for a container on the host's network. Only Instances at the container's own address get the flag. A Yard-scoped Service warns about each flagged Instance.
- The flag lives in reserved Consul meta, so other Workstations read it from the catalog too, and so changing it alone re-registers the Instance (for example `-p 80:80` on a container with no network address keeps the same host and port).

## Tried and ruled out
- Setting the flag from "no published port" alone: a container on the host's network has no published ports, yet sits at the LAN address and teammates reach it. That gave a false warning.
- Deriving the warning from the address (the Out-of-range Instance ranges): on its Home Workstation an Instance is never marked out of range, and a Docker network outside the configured ranges would be missed.

## Watch out
- "Unpublished" is also a Service state. GLOSSARY.md now says an unpublished container port is a different thing. Keep the code name `onHostPort` on BoundInstance, not `published`.

In the code: Registrations.UNPUBLISHED_CONTAINER_PORT (meta key `lvh-yard-unpublished-container-port`), BoundInstance.onHostPort, ContainerBindings.boundInstance, Instance.unpublishedContainerPort, ServiceWarning.Kind.UNPUBLISHED_CONTAINER_PORT. Test helper DashboardTest.startHostNetworkContainer.
