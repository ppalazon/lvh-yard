---
title: "A followed container or Compose service is refused whatever the port, and a name Binding and a Compose Binding are different targets"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-f7f48689
scope: repo
status: active
replaces: l-d22320f3
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerBindings.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/RegisterCompose.java
---

## Decided
- ContainerBindings.add refuses a target that a Binding of this Workstation already follows, whatever its port, with FollowedContainerException (409). So binding the same container and port again is a 409, not a no-op, because the user said that the port does not matter.
- A name Binding and a Compose Binding of one container are different targets, and both stay allowed.
- lvh-yard register compose cleans the Compose service name itself, with the same rule as the dashboard, because the cli module does not depend on the dashboard.

In the code: services/ContainerBindings.java (add, FollowedContainerException), ListedContainer.proposedService, cli RegisterCompose.java.
