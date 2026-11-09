---
title: "The first setup writes yard, consul-ui and traefik-admin once, and then they are ordinary Services"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 2, tokens: 11828 }
id: l-62e9dbd3
scope: repo
status: active
replaces: l-d2998a8b
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/FirstSetupComponentTest.java
---

## Decided
- The user found Core Services unneeded (ADR-0006). The first setup registers and publishes the three Services through the same paths as a user's request. After that, nothing protects them: no core mark, no 409, no fallback Route to the dashboard.
- The setup skips a name that already exists as a Service, also one that only Foreign Instances give. So it never publishes a Foreign yard Instance.
- The name consul is refused for a Service with 400, because Consul's catalog has its own entry with the name and ID consul. That entry is hidden by its name and ID together. If you remove the name rule, a user's consul Instances mix with Consul's own entry.

## Tried and ruled out
- Setup commands that lvh-yard init prints for the user to run: the user wanted hostnames with no extra steps.
- A one-time move of the old Core Services: nothing was released, so nothing needs a move.
- An in-memory list of the started names: it is lost when the dashboard stops between register and publish. The names key in Consul KV replaced it.
- To prove that a second start changes nothing through a check's output or Health: Consul keeps a check's status and output when the same registration comes again. Tests compare Instance IDs and count Instances instead.

## Watch out
- Tests cannot expect the three Services when the context is up. RemoteWorkstation.awaitFirstSetup waits for the marker key.
- The setup runs in the background after the start. A test that proves that it does nothing must wait a while with await().during.
- The remote dashboard of the tests starts on a new random port each time, so its yard Instance moves. A test that needs the Services as the setup writes them calls setUpAgain.

In the code:
- dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java: MARKER, NAMES, RETRY, TRY_LIMIT, setUp
- Consul.isServerItself, Registrations.CONSUL_REFUSAL, ServiceRecords.serviceExists
- Tests: FirstSetupComponentTest, FirstSetupIT, RemoteWorkstation.awaitFirstSetup
