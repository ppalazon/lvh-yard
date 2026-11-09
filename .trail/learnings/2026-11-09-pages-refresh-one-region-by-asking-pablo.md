---
title: "Pages refresh one region by asking for the whole page again, and the Reachability results stay outside it"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 24779 }
id: l-1ac93f26
scope: repo
status: active
replaces: l-7c8b73f0
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/Layout.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/CatalogPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
---

## Decided
- A page asks for itself again and HTMX keeps only the region (hx-select), so there is no fragment answer and no request header that changes what the server sends.
- What a refresh must not touch sits outside the region: the Catalog filter form and the Reachability test on the Service page.
- The Service page region always asks for the plain Service address, even on the Reachability results, because otherwise each refresh runs the test again.
- The Catalog filters are a GET form, so they live in the page address and the region asks for that address.
## Watch out
- HtmlFlow writes a selected option as selected="selected", not a bare selected.

In the code: Layout.refreshed, CatalogPage.Filters, ServicePage (region id service).
