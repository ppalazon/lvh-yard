---
title: "The risks box keeps its open state with a toggle listener and htmx:after:swap"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-9b3f4ece
scope: repo
status: active
replaces: l-f3b7ef95
src: agent
touches:
  - dashboard/src/main/resources/static/risks.js
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
---

## Decided
- risks.js keeps the open state of the risks box on the Service page (id risks) in a plain variable. The state lasts only while the page is open, as the spec asked.
- A toggle listener in the capture phase saves it, because toggle does not bubble. A document listener for htmx:after:swap sets it on the new box.

## Watch out
- In HTMX 4, htmx:after:swap with hx-swap=outerHTML fires on the new region, so a listener on document hears it. The HTMX 2 name htmx:afterSwap does not fire in HTMX 4.
- HtmlFlow's generated Details takes summary(String) and section(String) as text only. ServicePage uses custom("details") and custom("summary") instead, because the box needs attributes.

In the code: dashboard/src/main/resources/static/risks.js, ServicePage (the risks box).
