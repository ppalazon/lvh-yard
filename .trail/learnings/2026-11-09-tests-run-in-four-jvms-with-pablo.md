---
title: "Tests run in four JVMs with short timeouts, and an integration test stays only where no fake can serve it"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 1, tokens: 10566 }
id: l-1d5b1e51
scope: repo
status: active
replaces: l-1581a94f
src: agent
touches:
  - dashboard/pom.xml
  - mise.toml
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardComponentTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/consul/FakeConsul.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/events/EventDebouncer.java
---

## Decided
- The user wanted a fast check, so the tests have three kinds (ADR-0008). An integration test stays only where no unit or component test can replace it: the contract tests, real requests, Traefik, the first setup on a real Consul, one real WAN pause and three CLI process tests. In component tests, the other Workstation runs its real dashboard code on its own view of a shared FakeConsul.
- Surefire and Failsafe each run four JVMs. Each JVM has its own Spring context and test Yard, so classes in different JVMs share nothing. Four was as fast as six or eight, with less load on Docker.
- Tests set short times that users never change: Consul answer timeout 1 s, circuit breaker pause 2 s, Bound Instance check interval 1 s, event heartbeat 1 s. So a paused Workstation is found in about 4 s instead of 16 s.
- Before each test, both Consuls get back what they held after the first setup, and both Backups folders are emptied.
- Tests check one behavior, not exact text, HTML classes or layout.

## Tried and ruled out
- Classes side by side in one JVM: they share one Spring context and one Traefik on fixed ports, so they break each other.
- WireMock in place of Consul and Docker: tests write and then read back, and blocking queries and the Docker event stream are hard to fake over HTTP.
- Running only the tests of the changed package: most tests go through the REST API, so a change in one package breaks tests in others.

## Watch out
- If tests time out under heavy load, raise the 1 s answer timeout before you look elsewhere. It also applies to the test Workstation's own Consul.
- With 1 s checks, Consul changes more often than the 300 ms debounce window. So the debouncer sends at most 2 s after the first trigger.
- FakeConsul's views share one lock, the Federation object. Never lock on a FakeConsul instance.
- FakeConsul runs no checks. A check passes from what a test says listens.
- Busybox nc ends after each connection, even with -k, so test listeners loop nc -l.
- mise run test:unit needs -Dtest='*Test,!*ComponentTest'. A plain exclusion replaces Surefire's include pattern and runs the IT classes too.
- To run one integration test, use verify with -Dit.test=<Class>#<method> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false.
- Do not run mise run native and mise run check at the same time, because both clean cli/target.

In the code:
- dashboard/pom.xml forkCount, mise.toml test:unit and test:component
- DashboardComponentTest (properties, resetFakes), DashboardClient.newLanPort, DashboardIT, ConsulBaseline, RemoteWorkstation
- consul/FakeConsul.java, docker/FakeDocker.java, ConsulContract, DockerContract, events/EventDebouncer.java (LONGEST_WAIT)
