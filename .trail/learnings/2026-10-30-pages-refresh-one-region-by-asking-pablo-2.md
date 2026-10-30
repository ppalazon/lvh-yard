---
title: "Pages refresh one region by asking for the whole page again, and the Reachability results stay outside it"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 5, tokens: 27044 }
id: l-7c8b73f0
scope: repo
status: active
replaces: l-8928c82a
src: agent
---

## Decided
- Each page that refreshes has one region that HTMX asks for again every 5 seconds. The server sends the whole page back, and HTMX keeps only that region (hx-select). There is no separate fragment answer, so no request header changes what the server sends.
- Anything a refresh must not touch sits outside the region: the Catalog's filter form, and the Service page's Reachability test.
- The Service page's region always asks for the plain Service address, even on the Reachability results page. Otherwise every refresh would run the test again.
- The Catalog's filters are a plain GET form, so they live in the page address and the region asks for that same address.

## Watch out
- HTMX 4 swaps error answers too (it skips only 204 and 304). An error page has no region, so the region would vanish and its timer stop for good, for example while a Workstation is unreachable. The region therefore says hx-status:4xx and hx-status:5xx "swap:none".
- The tests share one Consul, and other tests leave Services in every state. A test cannot expect a filter to find no Services; the "No Services in this state." message has no test for this reason.
- HtmlFlow writes a selected option as selected="selected", not a bare selected.

In the code:
- Layout.refreshed(main, id, path, content) writes the region (hx-get, hx-trigger "every 5s", hx-select "#id", hx-swap outerHTML, hx-status swap:none).
- CatalogPage.Filters builds the Catalog address; region id "catalog". ServicePage region id "service".
- Tests: DashboardTest.openingTag and DashboardTest.div look inside page HTML without a parser.
