---
title: "register container binds a stopped container by name, and 400 problems name their field"
author: Pablo
date: 2026-11-04
branch: worktree-agent-a648892ddb78d9426
cost: { minutes: 30, tokens: 53627 }
id: l-283aa631
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerRegistration.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ApiProblems.java
---

Decided: ContainerRegistration.register (was ContainerImport.importContainer) treats a name that no running container has as a container that does not run, so the register endpoint never answers 404 and UnknownContainerException is gone. A stopped container is refused with 409 only when a Binding names that same container; a Compose Binding cannot match it, because a stopped container has no Compose labels to compare. The ticket asks for 400 'with field port', and the API problem had no field, so ApiProblems now adds a field property, from InvalidRegistrationException.field(), to every 400 of a registration. Watch out: the page form GET /containers/{container}/register still answers 404 for a container that is not in the Unfollowed list, because the form proposes the name and ports from the running container. The CLI register container prints that the Service gets its Bound Instance once a container starts when the answer has no instances.
