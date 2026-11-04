---
title: "The first setup runs once per Consul, so tests that restart ws-remote must set it up again"
author: Pablo
date: 2026-11-04
branch: worktree-agent-aae737aff38cb96f8
cost: { minutes: 6, tokens: 40937 }
id: l-5c54ca81
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/FirstSetupTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/RemoteWorkstation.java
---

## Decided
FirstSetup writes yard, consul-ui and traefik-admin through Registrations.register and Publications.publish, the same paths as a user's request, then writes the time at lvh-yard/first-setup. RemoteWorkstation.awaitFirstSetup waits only until that key exists, not for a time after the start, because a restart with the marker in place writes nothing.

## Watch out
ws-remote's dashboard starts on server.port=0, so after any restart its yard Instance still points at the port of the first start. A test that needs the remote yard, consul-ui or traefik-admin as the setup writes them calls FirstSetupTest.setUpAgain, which removes the three Services and the marker while the dashboard is stopped. A test that checks that the setup does nothing must wait a while (await().during), because the setup runs in the background after the start. trail callers did not find the InstancePriorityAliasTest caller of RemoteWorkstation.deregisterCheckInConsul; compile before you remove a test helper.
