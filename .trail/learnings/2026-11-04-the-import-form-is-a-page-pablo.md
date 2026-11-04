---
title: "The import form is a page of its own, and a default Service name is cleaned"
author: Pablo
date: 2026-11-04
branch: docker-import-03
cost: { minutes: 27, tokens: 17961 }
id: l-4a968456
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/UnfollowedContainer.java
---

Decided: choosing the port and the Service name happens on a page of its own, GET /containers/{container}/import, not in the list, because the list's #containers region swaps itself every 5 s and would reset what the user types. The list keeps the one-click Import for one-port rows and adds a Choose name or Choose port link. The page POST tells the two apart by the name field: the form always sends it, the one-click button sends nothing. A form failure answers 400 with the form, the error at its field and the typed values; a one-click failure still shows in the list row. The proposed Service name (UnfollowedContainer.service) is the Compose service or container name cleaned: lower case, anything outside [a-z0-9-] to -, cut to 63, no - at either end. Only the default is cleaned: a name the user gives is checked as typed, so my_app given by hand is still refused. lvh-yard import sends only the fields given, so the dashboard keeps its defaults, and --no-publish sends publish false.
