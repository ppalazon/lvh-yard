---
title: "The unpublished-port warning comes from a flag the Bound Instance stores, not from its address, and a host-network container never gets it"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 9, tokens: 113771 }
id: l-adacfbec
scope: repo
status: active
replaces: l-14222377
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainerBindingsComponentTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageComponentTest.java
  - GLOSSARY.md
---

## Decided
- When a Container Binding registers a Bound Instance, it records whether the Instance is on a port of the host: a published container port, or a container on the host network. A Yard-scoped Service warns about each Instance at the container's own address.
- The flag lives in reserved Consul meta, so other Workstations read it from the catalog, and a change of the flag alone registers the Instance again.

## Tried and ruled out
- The flag from "no published port" alone: a container on the host network has no published ports, but sits at the LAN address. That gave a false warning.
- The warning from the address, through the Out-of-range ranges: on its Home Workstation an Instance is never out of range, and a Docker network outside the ranges would be missed.

## Watch out
- "Unpublished" is also a Service state. Keep the code name onHostPort on BoundInstance, not published.

In the code: Registrations.UNPUBLISHED_CONTAINER_PORT, BoundInstance.onHostPort, ServiceWarning.Kind.UNPUBLISHED_CONTAINER_PORT, ContainerBindingsComponentTest (startHostNetworkContainer).
