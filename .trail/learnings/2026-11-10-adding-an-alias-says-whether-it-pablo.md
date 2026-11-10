---
title: Adding an Alias says whether it created the Service or joined an existing one
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-a65ea5f9
scope: repo
status: active
replaces: l-b7d9683e
src: agent
touches:
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java#aliasTo"
  - cli/internal/command/alias.go
---

Decided: the user wanted lvh-yard alias add and the catalog Alias button to show that the Alias joins an already created Service. Aliases.add returns AddedAlias(alias, serviceCreated). Its JSON is the Alias with @JsonUnwrapped plus serviceCreated, so the POST answer keeps the Alias fields and GET .../aliases (a list of Alias) stays without the field. serviceCreated is true only when the Service was missing (no record and no Instance); a Service that only Foreign Instances give answers false. lvh-yard alias add prints "Added the Alias to <remote> on <ws> to the existing Service <name>." or "Created Service <name> with Local Scope and added the Alias to <remote> on <ws>." Ruled out: a flash message on the Service page, because the pages keep no state between requests. In the code: the catalog form redirects to the Service page with #sources (InstanceSourcesCard.ID) when the Alias joined an existing Service, and without a fragment when it created one. Watch out: springdoc writes the unwrapped fields into the AddedAlias schema, so regenerate dashboard/openapi.yaml with mise run openapi after a change to Alias.
