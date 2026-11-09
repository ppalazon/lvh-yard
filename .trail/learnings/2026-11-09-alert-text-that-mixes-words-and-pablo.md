---
title: "Alert text that mixes words and a link goes in one paragraph, because the alert section is a grid"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 27212 }
id: l-0863213c
scope: repo
status: active
replaces: l-860240f4
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPage.java
---

## Decided
- Inside a Basecoat alert, text with a link in it goes in one p element, because otherwise each part takes its own row. The user once saw the words, the link and the period on three lines.
## Watch out
- Basecoat styles .alert > section as display: grid. Each loose text run and each inline element becomes its own grid item, so each takes a row.

In the code: the alerts in web/ServicePage.java and web/BackupsPage.java.
