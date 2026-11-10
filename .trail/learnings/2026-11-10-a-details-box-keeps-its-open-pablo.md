---
title: A details box keeps its open state through a refresh with data-preserve-open and disclosures.js
author: Pablo
date: 2026-11-10
branch: go-cli
id: l-03da5752
scope: repo
status: active
replaces: l-9b3f4ece
src: agent
touches:
  - dashboard/src/main/resources/static/disclosures.js
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/InstanceSourcesCard.java
---

## Decided
- disclosures.js keeps the open state of every details box that has data-preserve-open. The attribute value is the key. The risks box on the Service page uses it, and so does the Instance Sources card. The state lasts only while the page is open.
- The script reads the state just before each swap (htmx:before:swap) and sets it on the new boxes after the swap (htmx:after:swap). So a toggle that the user makes while a request runs is kept.

## Watch out
- In HTMX 4, htmx:after:swap with hx-swap=outerHTML fires on the new region, so a listener on document hears it. The HTMX 2 name htmx:afterSwap does not fire in HTMX 4.
- HtmlFlow's generated Details takes summary(String) and section(String) as text only. ServicePage uses custom("details") and custom("summary") instead, because the box needs attributes.

In the code: dashboard/src/main/resources/static/disclosures.js, ServicePage (the risks box), InstanceSourcesCard.
