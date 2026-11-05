---
title: "The risks box keeps its state with a toggle listener and htmx:after:swap"
author: Pablo
date: 2026-11-05
branch: worktree-agent-aeb3d1ecde7fa4102
cost: { minutes: 8, tokens: 1037 }
id: l-f3b7ef95
scope: repo
status: active
src: agent
touches:
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java#alerts"
---

## Decided
- risks.js keeps the open state of the Service page's risks box (id risks) in a plain variable. A toggle listener in the capture phase saves it, because toggle does not bubble. A document listener for htmx:after:swap sets it on the new box. The state lasts only while the page is open, as the spec asks.
- It is its own script file, not part of dialogs.js, so the modal work and this work do not touch the same file.

## Watch out
- In HTMX 4, htmx:after:swap with hx-swap=outerHTML fires on the new region (the swap task's target becomes the inserted element), so a listener on document hears it. In HTMX 2 the event name was htmx:afterSwap; that name does not fire in HTMX 4.
- HtmlFlow's generated Details takes summary(String) and section(String) as text only, with no attributes or children. ServicePage.alerts uses custom("details") and custom("summary") instead, as Dialogs uses custom("button").
