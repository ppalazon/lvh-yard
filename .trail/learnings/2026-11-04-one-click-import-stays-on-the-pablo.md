---
title: "One-click Import stays on the Containers list, and a followed container is refused"
author: Pablo
date: 2026-11-04
branch: docker-import-review
cost: { minutes: 30, tokens: 46027 }
id: l-228f65e3
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerImport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/UnfollowedContainer.java
---

Decided: the one-click Import of the list answers 303 to /containers?imported=<service>, and ContainersPage.render shows the confirmation (data-imported) above the #containers region, because the 5 s refresh selects only #containers and so keeps it. The import form still goes to the Service's page. ContainerImport.importContainer refuses a container that any Container Binding of this Workstation matches with FollowedContainerException (409, names the Service), because a second Binding of it would only add a second Route target. Publications.exists counts only this Workstation's Instances, Publication and Bindings (consul.health with no datacenter), so the 'new Service' rule already ignores other Workstations; importIntoAnUnpublishedServiceKeepsItUnpublished covers a terminal Instance with no Publication. UnfollowedContainer counts a container as Compose only when it has both Compose labels, so a project label alone binds by container name. The proposed name falls back from the cleaned Compose service to the cleaned container name to 'container', each kept only if Registrations.isDnsLabel accepts it, so the cleaning cannot drift from the name rule.
