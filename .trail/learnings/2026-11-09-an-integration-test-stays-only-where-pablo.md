---
title: "An integration test stays only where no fake can serve it: the other Workstation and the CLI's process run on fakes"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 28, tokens: 674560 }
id: l-1581a94f
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Import.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Profiles.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Shell.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/FakeShell.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardProcessIT.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTestSupport.java
  - dashboard/pom.xml
---

## Decided
The user ruled that an integration test must be one that no unit or component test can replace. Integration tests went from 180 to 47.
The other Workstation runs its real dashboard code in component tests: RemoteWorkstation(FakeConsul, FakeDocker) starts it on its own view of a FakeConsul whose datacenters every Workstation shares. FakeConsul counts FakeDocker's containers as listening, so Bound Instances pass. The CLI reads its process through Shell, and FakeShell gives tests an environment, typed input and a Java editor.
What stays real: the contract tests, ReachabilityIT (real requests), Traefik routing and status, the first setup on a real Consul, one real WAN pause, and three CLI process tests.

## Watch out
FakeConsul's views share one lock (the Federation object): never lock on a FakeConsul instance.
When a component test passes --lvh-yard.docker.host to the other Workstation, it gets the real DockerEngine for that run, because the test is about how a missing or unreadable socket fails.
In native mode the six editor tests skip: the executable cannot run a Java editor.
Do not run mise run native and mise run check at the same time: both clean cli/target.

In the code: FakeConsul.as, RemoteWorkstation fake mode, DashboardComponentTest.Fakes, cli Shell and FakeShell, YardTestSupport helpers.
