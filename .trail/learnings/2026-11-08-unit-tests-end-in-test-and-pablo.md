---
title: "Unit tests end in Test and run with surefire, integration tests end in IT and run with failsafe"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 13, tokens: 114443 }
id: l-2118a3a2
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainerBindingsIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainersIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/HealthChecksIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/PublicationsIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/StaticInstancesIT.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/services/ContainerBindingsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/services/InstanceKindTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/services/RegistrationsTest.java
---

## Decided
The user asked for fast unit tests next to the slow integration tests (docs/adr/0008). A unit test starts no Spring context, no Docker container and no process. Edge cases of a rule go to unit tests, and each feature keeps one or a few IT tests for the wiring. mise run test:unit runs the unit tests of both modules in about 5 s. mise run check still runs everything, about 55 s.
The Instance Priority tests were split into seven classes only so that surefire ran them side by side while Bound Instances waited 10 s for a check. With 1 s checks in tests, they are one InstancePriorityIT, and RoutingTest covers each case of Routing.of.

## Tried and ruled out
Running only the tests of the changed package: integration tests go through the REST API, so a change in one package breaks tests in others.

## Watch out
To run one integration test, use -Dit.test=<Class>#<method> with the verify phase, and -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false to skip the unit tests. mise run openapi does this.
An unknown Scope in a Publication request is refused by Jackson while it reads the body, so only PublicationsIT.unknownScopeIsRefused covers it.

In the code: dashboard/pom.xml and cli/pom.xml (maven-failsafe-plugin), mise.toml test:unit and openapi, YardTestSupport, YardProcessIT, services/RoutingTest, RegistrationsTest, ContainerBindingsTest, InstanceKindTest.
