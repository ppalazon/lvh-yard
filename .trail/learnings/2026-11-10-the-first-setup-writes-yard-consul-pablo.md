---
title: "The first setup writes yard, consul-ui and traefik-admin once, and then they are ordinary Services"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 2, tokens: 11828 }
id: l-4f8661e4
scope: repo
status: active
replaces: l-62e9dbd3
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/FirstSetup.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Registrations.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/consul/Consul.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/FirstSetupComponentTest.java
---

## Decided
- The user found Core Services unneeded (ADR-0006). The first setup writes the three Services through the same paths as a user's request, and after that nothing protects them.
- The setup skips a name that already exists as a Service, so it never publishes a Foreign yard Instance.
- The name consul is refused for a Service, because Consul's catalog has its own entry with that name and ID.

## Tried and ruled out
- Setup commands that init prints: the user wanted hostnames with no extra steps.
- An in-memory list of started names: it is lost when the dashboard stops halfway. The names key in Consul KV replaced it.
- Proving that a second start changes nothing through check output: Consul keeps a check's output when the same registration comes again. Tests compare IDs instead.

## Watch out
- Tests wait for the marker key with RemoteWorkstation.awaitFirstSetup, because the setup runs in the background.
- A test that proves that the setup does nothing must wait with await().during.
- The remote test dashboard gets a new port on each start, so its yard Instance moves. Call setUpAgain.

In the code: services/FirstSetup.java, Registrations.CONSUL_REFUSAL, FirstSetupComponentTest, FirstSetupIT.
