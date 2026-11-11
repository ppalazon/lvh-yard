---
title: Changes to a Remote Service are refused with an explained client error
author: Pablo
date: 2026-10-30
id: l-c3ab1ad4
scope: repo
status: active
src: init-sessions
---

## Decided
- Write paths never name a Workstation, so a Remote Service cannot be addressed for change. Ticket 05 wanted a clear reason, so the API answers 405 with an explanation.
- In the code: In ServicesController, writes under /api/services/** for a non-home Service return 405 with the reason: change it on its Home Workstation. The Remote Service page shows no forms or buttons.

_From past Claude sessions here, Oct 30._
