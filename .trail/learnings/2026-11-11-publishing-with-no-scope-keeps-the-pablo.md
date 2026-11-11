---
title: "Publishing with no Scope keeps the Scope that a Publication has, and loopback warnings come from the host text only"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-44d8b63a
scope: repo
status: active
replaces: l-374eb69a
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceWarning.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/RouteBuilder.java
  - cli/internal/command/publication.go
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/TraefikConfigurationComponentTest.java
---

## Decided
- The user decided that publishing with no Scope gives a new Publication Local Scope and leaves the Scope of an existing one alone, so publishing again never quietly stops sharing. Only an explicit scope, such as --scope local, changes it back.
- A Remote Service's Traefik router and backend are named <service>.<workstation>. A Service name is one DNS label with no dot, so it never clashes with an own Service.
- Loopback warnings (LOOPBACK_INSTANCE) come from the host text only, never from a DNS lookup: IP literals, localhost and *.localhost. So a hostname that only resolves to 127.0.0.1, such as one under lvh.me, is not caught.
- The user chose not to include 192.168.0.0/16 in the default Out-of-range ranges, because most home LANs use it. Loopback is in the list, not fixed, because the test Yard runs both Workstations on the loopback of one computer and must leave it out. YARD_OUT_OF_RANGE set but empty means that nothing is out of range.

## Watch out
- The fault test (TraefikConfigurationComponentTest) writes a Publication with an unknown Scope straight into the Consul KV of the other Workstation. It deletes it in finally, because while it is there, every read of that Workstation fails.

In the code: services/Publications.java (publish), ServiceWarning.Kind.LOOPBACK_INSTANCE, routes/RouteBuilder.java (name), cli/internal/command/publication.go.
