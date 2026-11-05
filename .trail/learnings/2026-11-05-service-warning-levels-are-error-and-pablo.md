---
title: "Service Warning levels are error and risk, and risks stay folded on the Service page"
author: Pablo
date: 2026-11-05
branch: main
cost: { minutes: 23, tokens: 108305 }
id: l-c6505dd8
scope: repo
status: active
src: agent
touches:
  - ".scratch/dashboard-ux/issues/01-phyxor-theme.md"
  - ".scratch/dashboard-ux/issues/02-external-links-in-a-new-tab.md"
  - ".scratch/dashboard-ux/issues/03-first-modal-static-instances.md"
  - ".scratch/dashboard-ux/issues/04-modals-for-other-create-forms.md"
  - ".scratch/dashboard-ux/issues/05-catalog-filters-on-change.md"
  - ".scratch/dashboard-ux/issues/06-service-warning-levels.md"
  - ".scratch/dashboard-ux/issues/07-service-page-alerts-and-risks.md"
  - ".scratch/dashboard-ux/issues/08-service-page-cards.md"
  - ".scratch/dashboard-ux/spec.md"
  - GLOSSARY.md
---

## Decided
- A Service Warning has two levels: error (the hostname does not work) and risk (it works, but something can go wrong). The user first agreed to "error" and "warning", then accepted "risk", because "warning" already means a Health result and a Status level.
- The user wants risks only as reminders. They sit in one closed details box under the red alerts, never as open alerts.
- The Service state (Unavailable, Unhealthy) shows as a red alert on the page, but it is not a Service Warning and the API does not add it to warnings.

## Tried and ruled out
- Severity values error/warning: a "warning with severity warning" was a third meaning of the word.
- A browser test tool such as Playwright: the user accepted HTML checks in page tests plus a check by hand on the test Workstations.

## Watch out
- The Service page refreshes every 5 seconds, so an open details box closes after a swap unless a script keeps its state.

In the code: .scratch/dashboard-ux/spec.md, GLOSSARY.md (Service Warning), ServiceWarning.Kind gets a fixed severity in ticket 06.
