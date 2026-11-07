---
title: "Alert text that mixes words and a link goes in one paragraph, because the alert section is a grid"
author: Pablo
date: 2026-11-07
branch: main
cost: { minutes: 5, tokens: 9679 }
id: l-860240f4
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainersTest.java
---

## Decided
- Text inside a Basecoat alert that has a link in it sits in one p element. The user saw "Registered in the Service", the Service link and the period on three lines.

## Watch out
- Basecoat styles .alert > section as display: grid. In a grid, each loose text run and each inline element becomes its own grid item, so each takes a row.

In the code:
- ContainersPage.render, the data-registered alert: section > p > text, a, text.
- ContainersTest checks the whole "<p>Registered in the Service <a ...>name</a>.</p>".
