---
title: "The Yard API separates this Workstation from the others, and plain list shows the whole Yard"
author: Pablo
date: 2026-10-30
standing: true
id: l-e6d6a8a6
scope: repo
status: active
src: init-sessions
---

## Decided
- The first design returned one flat list of Workstations. It was changed so this Workstation, which always answers, differs in type from other Workstations, which can be unreachable. The user also reversed the earlier choice that plain `yard list` shows only local Services.
- In the code: GET /api/yard returns {home: HomeWorkstation, remotes: [YardWorkstation]}. Only YardWorkstation has `unreachable`. /api/workstations was renamed. /api/services stays flat. `yard list --workstation <name>` exits non-zero on an unknown name because the API answers 404.

_From past Claude sessions here, Oct 30._
