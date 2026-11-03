---
title: "The dashboard tests run in four JVMs at once, with short Consul timeouts, and the check builds both modules at the same time"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 4, tokens: 24841 }
id: l-f4cc240c
scope: repo
status: active
replaces: l-41e6ea48
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/ConsulSettings.java
  - dashboard/pom.xml
  - mise.toml
---

## Decided
mise run check took about 1 min 51 s. Now it takes about 32 s.
Each surefire JVM starts its own Spring context and its own test Yard of containers, so test classes in different JVMs do not share Consul, Traefik or ports. Four JVMs gave about 37 s. Six gave 31 s and eight 32 s, so four keeps the container load low for little loss. The cli module does not depend on the dashboard, so Maven builds both at the same time.
The Consul answer timeout (3 s) and the pause of the YardReader circuit breaker (10 s) are settings with those defaults. The tests set 1 s and 2 s, so a paused ws-remote is found unreachable, and back, in about 4 s instead of 16 s. They are not in application.yaml or .env, because users have no reason to change them.

## Tried and ruled out
Parallel classes inside one JVM: all classes share one Spring context, one Traefik on fixed ports, and RemoteWorkstation.stopAnswering() pauses the shared remote Consul. Classes would break each other.
A second, short ConsulSettings constructor for tests: a record with two constructors confuses Spring's constructor binding. ConsulStatusTest has its own settings() helper instead.

## Watch out
The load of parallel JVMs showed a race in listenOnLanAddress: busybox nc ends after each connection, even with -k. The Consul TCP check connected first, and the Reachability test then got "Connection refused". The listener now restarts nc in a loop.
The 1 s answer timeout in tests also applies to reads of the test Workstation's own Consul. If tests time out under heavy load, raise it before you look elsewhere.

In the code: dashboard/pom.xml (maven-surefire-plugin forkCount 4), mise.toml check task (./mvnw -q -T 2 clean verify), ConsulSettings answerTimeout and unreachablePause, DashboardTest properties, DashboardTest.listenOnLanAddress.
