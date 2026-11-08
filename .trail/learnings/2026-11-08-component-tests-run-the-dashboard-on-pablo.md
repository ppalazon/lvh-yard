---
title: "Component tests run the dashboard on FakeConsul and FakeDocker, and integration tests keep only what needs a real system"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 34, tokens: 261162 }
id: l-050b033a
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/BoundInstancesIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardClient.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/InstancePriorityIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/TestcontainersConfiguration.java
  - cli/pom.xml
  - dashboard/pom.xml
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/AliasesComponentTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/AliasesIT.java
---

## Decided
The user asked for three kinds of tests (docs/adr/0008): unit, component (*ComponentTest, the dashboard through HTTP with FakeConsul and FakeDocker) and integration (*IT, at least one real system). WireMock was rejected: tests write then read back, and blocking queries and the Docker event stream are hard to fake over HTTP.
Docker and Consul are interfaces (DockerEngine, ConsulAgent). ConsulWatcher's blocking queries moved into Consul.awaitChange so the fake can answer them. DockerContract and ConsulContract run the same cases on fake and real.
Integration classes were split by test: a test stays *IT only if it uses the other Workstation's own dashboard, the Consul container, the Yard network, a real container or Traefik.

## Watch out
Docker sends an empty IP, not null, for a port that is not published. The contract found it.
FakeConsul runs no checks: a check passes or fails from what a test says listens (listen, answerHttp), with Consul's output text. The component Yard has the datacenter ws-remote with nothing in it.
Maven runs surefire (unit and component) before failsafe, so the component tests add to the check time instead of running beside the integration tests.
mise run test:unit needs -Dtest='*Test,!*ComponentTest'. A plain exclusion replaces surefire's include pattern and runs the *IT classes too.

In the code: dashboard/src/test/.../consul/FakeConsul.java, docker/FakeDocker.java, DashboardClient, DashboardComponentTest, DashboardIT, mise.toml test:unit and test:component.
