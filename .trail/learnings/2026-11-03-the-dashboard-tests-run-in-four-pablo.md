---
title: "The dashboard tests run in four JVMs at once, and the check builds both modules at the same time"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 12, tokens: 70585 }
id: l-41e6ea48
scope: repo
status: active
src: agent
touches:
  - dashboard/pom.xml
  - mise.toml
  - docs/development.md
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
---

## Decided
mise run check took about 1 min 51 s. The dashboard tests took 1 min 39 s of it, one class after another. Each surefire JVM starts its own Spring context and its own test Yard of containers, so test classes in different JVMs do not share Consul, Traefik or ports. Four JVMs bring the check to about 37 s. Six gave 31 s and eight 32 s, so four keeps the container load low for little loss. The cli module does not depend on the dashboard, so Maven builds both at the same time.

## Tried and ruled out
Parallel classes inside one JVM: all classes share one Spring context, one Traefik on fixed ports, and RemoteWorkstation.stopAnswering() pauses the shared remote Consul. Classes would break each other.

## Watch out
The load of parallel JVMs showed a race in listenOnLanAddress: busybox nc ends after each connection, even with -k. The Consul TCP check connected first, and the Reachability test then got "Connection refused". The listener now restarts nc in a loop.
The slowest single tests (about 16 s each) wait for the circuit breaker in YardReader: two 3 s Consul timeouts and 10 s open. Making those durations configurable for tests can cut more time.

In the code: dashboard/pom.xml (maven-surefire-plugin forkCount 4), mise.toml check task (./mvnw -q -T 2 clean verify), DashboardTest.listenOnLanAddress.
