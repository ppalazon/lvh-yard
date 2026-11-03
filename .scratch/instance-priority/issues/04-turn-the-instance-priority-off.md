# 04: Turn the Instance Priority off for a Service

**What to build:** The user runs `lvh-yard publish web --priority off` (or turns it off on the Service page). Then `web.lvh.me` sends requests to every passing Static and Bound Instance of `web` together, as before Instance Priority. See `.scratch/instance-priority/spec.md`, stories 23 to 31.

**Blocked by:** 03

**Status:** done

- [x] An empty order (`priority: []`) means off. The API accepts it and stores it with the Publication.
- [x] While off, the Route uses every passing in-range Static and Bound Instance.
- [x] If none passes while off, the Route uses every in-range Instance.
- [x] While off, a same-named Alias stays `hidden` and is never used
- [x] Publishing again without `priority` keeps off.
- [x] `--priority` with kinds turns it on again.
- [x] `lvh-yard publish --priority off` sends `[]`, and the help names `off`.
- [x] The Service page has "Turn off" and "Use an order" controls and shows `Routing to: every passing Instance (Instance Priority off)`
- [x] `openapi.yaml`, `docs/user-guide.md`, `GLOSSARY.md` (Instance Priority) and ADR-0005 (Consequences) say what off does
- [x] A Yard-scoped Service that is off on its Home Workstation is off on every Workstation
