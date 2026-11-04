---
title: "Importing a container gives it a Service, and a failed import stops the Containers page refresh"
author: Pablo
date: 2026-11-04
branch: docker-import-02
cost: { minutes: 25, tokens: 41452 }
id: l-bbb566d8
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerImport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java
---

Decided: ContainerImport.importContainer asks Publications.exists(service) BEFORE ContainerBindings.add, because add makes the Service exist, and only a Service that did not exist is published (Local). So an unpublished Service stays unpublished, and an existing Publication keeps its Scope. The request body {port, service, publish} is all optional; with no port, the container must have exactly one distinct TCP container port, else a 400 with field 'port' that lists the ports in the message (the CLI prints only the detail). The page POST /containers/{container}/import goes to the Service's page on success (303). On a failure it answers 400 with the whole Containers page and the reason in the row of that container, and that page's #containers region has no hx-trigger, because the 5 s refresh would swap in a list without the reason. Only one-port rows get the Import button; port choice and name editing are ticket 03.
