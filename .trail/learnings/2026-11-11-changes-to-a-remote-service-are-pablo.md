---
title: Changes to a Remote Service are refused with an explained client error
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-f983a88a
scope: repo
status: active
replaces: l-c3ab1ad4
src: agent
touches:
  - "dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ServicesController.java#change"
  - docs/user-guide.md
---

## Decided
- Write paths never name a Workstation, so a Remote Service cannot be addressed for change. The API answers 405 with an explanation.
- In the code: In ServicesController.change, writes under /api/services/** return 405 with the reason: change it on its Home Workstation. The Remote Service page shows no forms or buttons.
