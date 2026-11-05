---
title: "Adding an Alias never asks the other Workstation, so it works while that Workstation is off"
author: Pablo
date: 2026-11-05
branch: main
cost: { minutes: 1, tokens: 915 }
id: l-8c6bb4a5
scope: repo
status: active
src: agent
---

## Decided
- The user asked that adding an Alias never checks the other Workstation. Before, the API, the Aliases page and a Backup import looked up the target Service live, and refused with 404 or 503 when the Workstation or Service was missing or did not answer. That blocked a restore without a network.
- Now an Alias is stored at once. Its state (missing, unreachable, not-shared and so on) and its Route come from the Yard as the Routes see it, so the Alias gets a Route only when its Workstation answers and has the Service with Yard Scope.
- What is still refused: an Alias name that breaks the Service name rules, a Workstation or Service name that is not one DNS label, and an Alias to this Workstation.
- This takes over from the lines in the import learnings that say an added or changed Alias has its target looked up.

## Watch out
- The Aliases page still offers only the Remote Services that it can read now. The API, the CLI and an import can name any target.

In the code: Aliases.add and Aliases.checkTarget (dashboard/.../services/Aliases.java); ImportPlanner.Run.alias no longer uses YardReader; the PUT /api/workstation/aliases/{alias} answer has no 404 or 503 any more.
