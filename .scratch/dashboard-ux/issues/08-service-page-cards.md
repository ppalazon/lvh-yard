# 08: Service page summary cards and section grid

**What to build:** The Service page reads as a dashboard. A row of summary cards shows the state at a glance. Section cards hold the Publication, Instance Priority and Route Profiles, and the tables take the full width. See `.scratch/dashboard-ux/spec.md`, user stories 43 to 52.

**Blocked by:** 01 (Phyxor theme with system fonts), 07 (Service page alerts and the risks box).

**Status:** done

- [x] Under the alerts is a row of summary cards for State, Publication, passing Instances, and the Instance kind that routes now.
- [x] The Publication card shows the hostname, the Scope and the access log, or "Not published" for an Unpublished Service.
- [x] The passing card shows the passing Instances out of all Instances.
- [x] The routing card follows the Instance Priority, or says that it is off.
- [x] Publication with its buttons, Instance Priority and Route Profiles sit in section cards. The grid has two columns on a wide screen and one on a phone.
- [x] A card that has nothing to show is left out, as today.
- [x] The Instances table, Container Bindings and Reachability take the full width, in cards.
- [x] The page keeps its 5-second refresh and its on-demand Reachability test.
- [x] A Remote Service shows the same cards and alerts without the change buttons.
- [x] Page tests cover each summary card, an Unpublished Service, and a Remote Service with no change buttons.
- [ ] You try the layout by hand on a wide screen and at phone width on `mise run dev:up`.
