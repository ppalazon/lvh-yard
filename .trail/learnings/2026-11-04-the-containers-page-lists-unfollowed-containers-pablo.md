---
title: The Containers page lists unfollowed containers and explains Docker failures
author: Pablo
date: 2026-11-04
branch: docker-import-01
cost: { minutes: 16, tokens: 33832 }
id: l-07861034
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java
---

Decided: ContainerImport.unfollowed() drops a container that any Binding of this Workstation matches (ContainerBindings.matches), whatever the Binding's port, because a second import of the same container would only add a second Route target. When Docker fails, GET /api/workstation/containers answers 503 (UnreadableDockerException), but the /containers page answers 200 with the reason inside its refreshed region, because the refresh swaps nothing on an error answer, so a 503 would never show the message. The reason text comes from DockerFailures, shared with DockerStatus, so the status page and the Containers page say the same thing. HEALTHCHECK state is Health.Status from /containers/json, with the '(healthy)' end of Status as the fallback for older Docker APIs. Watch out: ContainersTest uses busybox:1.37, not the Consul image, because the Consul image exposes about 8 ports of its own and a test could not show 'one port'. Tests assert only on their own containers, never on the whole list, because other JVMs and the developer share the same Docker.
