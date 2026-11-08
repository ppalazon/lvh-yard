---
title: "Each dashboard test starts from the test Yard as it was after the first setup, and Bound Instances check every second in tests"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 30, tokens: 212935 }
id: l-bf108138
scope: repo
status: active
src: agent
touches:
  - docs/development.md
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainerBindingsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainersTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageTest.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/events/EventDebouncer.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/PublicationsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/StaticInstancesTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ForeignInstanceTest.java
---

## Decided
The user found mise run check too slow (81 s, with flaky StatusTest and ContainersTest failures). They kept one test Yard per JVM, four JVMs, and asked that the data be cleared between tests instead of one shared Yard for all tests, because one JVM ran the classes one after the other in 1 min 51 s.
Before each test, both Consuls get back the KV and the registrations they held just after the first setup, both Backups folders are emptied, and a paused ws-remote is unpaused and waited for. A paused ws-remote had left a failed WAN member for the next test.
Bound Instances took their 10 s check interval from a constant. It is now a setting with a 10s default, and the tests set 1s. The tests also set the event heartbeat to 1s.
Tests check one behavior, not exact text, HTML classes or layout. The writing rules (no docker compose, no mise in user text) are not tested.
Result: about 59 s.

## Tried and ruled out
A debouncer window that starts at the first trigger and never moves: two containers that start together sent two events, and ContainersTest expects one.

## Watch out
With checks every second, Consul changes more often than the 300 ms debounce window. The window used to restart at each trigger, so no event went out. The debouncer now sends at most 2 s after the first trigger.
Tests with a Static Instance and no check wait for the 10 s default check. Give them a 1 s interval and a newLanPort, not a fixed port.

In the code: ConsulBaseline (test), DashboardTest.connect and awaitRemoteAnswers, RemoteWorkstation.reset, Registrations boundCheckInterval (lvh-yard.bound-instances.check-interval), EventDebouncer.LONGEST_WAIT.
