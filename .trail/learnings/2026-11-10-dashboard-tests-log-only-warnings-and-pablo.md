---
title: "Dashboard tests log only warnings, and test settings go in the Maven test plugins, not in a test application.yaml"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 20, tokens: 257902 }
id: l-4f2fb3a8
scope: repo
status: active
replaces: l-3dcbc921
src: agent
touches:
  - docs/user-guide.md
  - docs/adr/0001-one-consul-datacenter-per-workstation.md
  - docs/adr/0002-traefik-reads-routes-from-lvh-yard.md
  - docs/adr/0003-hostnames-name-the-home-workstation.md
  - docs/adr/0004-route-profiles-travel-through-consul-kv.md
  - docs/adr/0006-lvh-yard-sets-up-its-own-services-once.md
  - docs/adr/0007-reactive-dashboard-with-sse-and-htmx.md
  - docs/adr/0008-unit-component-and-integration-tests.md
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/CreateAndDeleteServicesComponentTest.java
---

## Decided
- The user wanted a passing check to print little, because an agent reads all of its output. A test-only Logback file sets the root logger to WARN. Two library loggers are at ERROR, because each test JVM repeats their warning. The Spring banner is off through a system property in Surefire and Failsafe.

## Tried and ruled out
- A test application.yaml for the banner: a file of that name on the test classpath hides the main application.yaml, so the dashboard would lose its own settings in tests.

## Watch out
- The warnings that are left come from tests that cause them on purpose, such as a missing Docker socket. They do not mean that a test failed.

In the code: dashboard/src/test/resources/logback-test.xml, dashboard/pom.xml (systemPropertyVariables).
