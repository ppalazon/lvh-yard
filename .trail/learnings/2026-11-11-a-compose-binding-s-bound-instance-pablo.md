---
title: "A Compose Binding's Bound Instance ID names the Binding, so one container bound two ways gets two Instances"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-370c8cae
scope: repo
status: active
replaces: l-2f6c98cd
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerBindingRequest.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerBinding.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerBindings.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/DashboardClient.java
---

## Decided
- A Bound Instance's Consul ID stays `<service>:<container>:<port>` for a Binding by container name, so earlier IDs do not change. For a Compose Binding it is `<service>:compose:<project>:<compose-service>:<port>:<container>`. A container can match both a name Binding and a Compose Binding. With the old ID, both Bindings wrote one registration, and removing either one removed the shared Instance until the next Docker event.
- A Compose Binding's ID is `compose:<project>:<service>:<port>`. Docker container names cannot hold `:`, so it never clashes with `<container>:<port>`. It has no `/`, so it fits one URL path segment.
- Containers of a Compose service are found only by Compose's labels `com.docker.compose.project` and `com.docker.compose.service`, never by name.

## Tried and ruled out
- Tests that name containers `<project>-<service>-<n>`, as Compose does: they would still pass if the code matched by name prefix. The tests give Compose's labels to containers with unrelated names instead, and do not run `docker compose`.

In the code: dashboard/.../services/Registrations.java (BoundInstance.id), dashboard/.../services/ContainerBinding.java (id), dashboard/.../docker/Docker.java (Container.isOfComposeService), dashboard/src/test/.../DashboardClient.java (startComposeContainer).
