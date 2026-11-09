---
title: "Service Warning levels are error and risk, and risks stay folded on the Service page"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 24779 }
id: l-8e0ead7e
scope: repo
status: active
replaces: l-c6505dd8
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceWarning.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
  - dashboard/src/main/resources/static/risks.js
---

## Decided
- A Service Warning is an error (the hostname does not work) or a risk (it works, but something can go wrong). The user accepted "risk", because "warning" already means a Health result and a Status level.
- Risks are reminders only. They sit in one closed details box under the red alerts.
- The Service state (Unavailable, Unhealthy) shows as a red alert, but it is not a Service Warning, and the API does not list it in warnings.
## Tried and ruled out
- Severity values error and warning: a "warning with severity warning" was a third meaning of the word.
- A browser test tool such as Playwright: the user accepted HTML checks in page tests plus one look by hand.
## Watch out
- A refresh swaps the box for a closed one, so risks.js keeps its state.

In the code: ServiceWarning.Kind and Severity, ServicePage, dashboard/src/main/resources/static/risks.js.
