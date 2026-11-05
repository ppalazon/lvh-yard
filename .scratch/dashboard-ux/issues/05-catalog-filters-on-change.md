# 05: Catalog filters that load on change

**What to build:** On the Catalog, a new Workstation or state choice loads the list at once, with no Apply button. The page address keeps the filters, so a reload, a shared link and the Back button work. See `.scratch/dashboard-ux/spec.md`, user stories 20 to 26.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] The Workstation and State selects send an HTMX `GET` on `change`. The answer replaces only the Catalog list region.
- [ ] `hx-push-url` puts the filters in the page address.
- [ ] The Apply button is gone.
- [ ] The 5-second refresh of the list uses the filters of the current address.
- [ ] A direct load of a filtered address renders the full page with those filters.
- [ ] The Route Profiles "Example" select does not change.
- [ ] Page tests cover the HTMX attributes, the missing Apply button, and a filtered address.
- [ ] You try a filter change, a reload and the Back button by hand on `mise run dev:up`.
