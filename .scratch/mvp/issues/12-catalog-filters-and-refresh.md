# 12: Catalog filters and live refresh

**What to build:** A developer filters the Catalog by Workstation and by state, and every page refreshes itself, so containers starting and stopping show without reloading.

**Blocked by:** 03 (Publish a Service on its own Workstation), 05 (See Remote Services across the Yard)

**Status:** ready-for-agent

- [ ] The Catalog and the API filter Services by Workstation and by state.
- [ ] The filters stay in the page address, so a filtered Catalog can be bookmarked.
- [ ] The Catalog and the Service detail page refresh their content every 5 seconds through HTMX, without a full reload.
- [ ] A refresh keeps the filters and any form that is being filled in.
