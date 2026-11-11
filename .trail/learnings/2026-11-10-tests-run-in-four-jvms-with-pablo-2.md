---
title: "Tests run in four JVMs with short timeouts, and an integration test stays only where no fake can serve it"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-18b084fe
scope: repo
status: active
replaces: l-b35bf118
src: agent
touches:
  - dashboard/pom.xml
  - mise.toml
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/DashboardComponentTest.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/consul/FakeConsul.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/events/EventDebouncer.java
---

## Decided
- The user wanted a fast check (ADR-0008). An integration test stays only where no fake can serve it. In component tests, the other Workstation runs its real dashboard code on a shared FakeConsul.
- Surefire and Failsafe each run four JVMs, each with its own Spring context and test Yard. Four was as fast as six or eight, with less load on Docker.
- Tests set short times: Consul answer timeout 1 s, circuit breaker pause 2 s, check interval 1 s, event heartbeat 1 s. So a paused Workstation is found in about 4 s.

## Tried and ruled out
- Classes side by side in one JVM: they share one Spring context and one Traefik.
- WireMock for Consul and Docker: blocking queries and the Docker event stream are hard to fake over HTTP.

## Watch out
- If tests time out under load, raise the 1 s answer timeout first.
- With 1 s checks, the debouncer sends at most 2 s after the first trigger.
- Never lock on a FakeConsul instance, because its views share the Federation lock.
- Busybox nc ends after each connection, so test listeners loop nc -l.
- test:unit needs -Dtest='*Test,!*ComponentTest', because a plain exclusion also runs the IT classes.

In the code: dashboard/pom.xml, mise.toml, DashboardComponentTest, consul/FakeConsul.java, events/EventDebouncer.java.
