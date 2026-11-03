---
title: Core Services are kept on start with fixed IDs
author: Pablo
date: 2026-11-03
branch: core-services-02
cost: { minutes: 9, tokens: 37597 }
id: l-b9121120
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
---

- decided: CoreServices is a SmartLifecycle. Its start() keeps the Core Services once, synchronously, so they exist as soon as the context is up (tests need no await). Only when Consul fails does it retry on a virtual thread every 5 s. It runs after the web server, so local.server.port is known (tests and the remote test dashboard use port 0).
- decided: Core Instances have fixed IDs lvh-yard-core-<name>, meta lvh-yard-instance=static plus lvh-yard-core=true. The core flag of a Service is 'any Instance has lvh-yard-core', read from the catalog, so a teammate's Core Services are marked too.
- decided: the Consul server's own catalog entry (name consul, ID consul) is filtered per Instance (Consul.isServerItself) instead of dropping the whole name consul, which now is a Core Service.
- ruled out: proving 'a second start changes nothing' by a check's output or Health. Consul keeps a check's status and output when the same registration is sent again, so an unconditional re-register looks the same through the API. The test checks IDs and no duplicate Instances instead.
- watch out: the test Consul runs checks inside its container, so every Core Instance's check (127.0.0.1) is critical in tests. Test that the checks exist, not that they pass.
