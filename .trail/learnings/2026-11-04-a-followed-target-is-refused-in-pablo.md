---
title: "A followed target is refused in ContainerBindings.add, and the bind form shows it at its field"
author: Pablo
date: 2026-11-04
branch: worktree-agent-a3d668fa4db150f27
cost: { minutes: 22, tokens: 59026 }
id: l-d22320f3
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerBindings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/RegisterContainer.java
---

Decided: ContainerBindings.add throws FollowedContainerException (409) when any Binding of this Workstation has the same container name, or the same Compose project and service, whatever its port. So binding the same container and port again is now 409, not a no-op, because the ticket says the port does not matter. A name Binding and a Compose Binding of one container are different targets and both stay allowed. The Containers page bind form (POST /containers/bind) turns that 409 into a 400 with the reason at the container or composeService field, through FollowedContainerException.field(); that is why InvalidRegistrationException's constructor is public. Publications.addPublishingNew now checks the Service name first: an empty name reached consul.health in exists() and answered 500. The form hides the fields of the other kind with a :has() rule in app.css, not wrapper divs, because InstanceForm.text takes a Form, not a Div. The CLI register container --compose cleans the Compose service name itself (same rule as UnfollowedContainer.cleaned), because the cli module does not depend on the dashboard.
