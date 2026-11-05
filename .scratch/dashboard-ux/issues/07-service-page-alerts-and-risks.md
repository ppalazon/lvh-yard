# 07: Service page alerts and the risks box

**What to build:** The Service page shows red alerts at the top for what stops the hostname from working. Under them, one closed box holds the risks to keep in mind. The box keeps its open or closed state across the 5-second refresh. See `.scratch/dashboard-ux/spec.md`, user stories 32 to 41.

**Blocked by:** 01 (Phyxor theme with system fonts), 06 (Service Warning levels in the API and the CLI).

**Status:** done

- [x] The old "Check this Service" box is gone.
- [x] Each `error` Service Warning gives its own red alert under the title.
- [x] An Unavailable Service gives the red alert "No Instances are registered". An Unhealthy Service gives "No Instance passes its checks".
- [x] All `risk` Service Warnings sit in one `<details>` element with the risk style, closed by default, under the red alerts.
- [x] Its summary says "1 risk to keep in mind" or "N risks to keep in mind".
- [x] If there are no risks, the page leaves out the element.
- [x] The page shell script remembers whether the risks box is open, and sets it again after each HTMX swap of the region. The memory lasts only while the page is open.
- [x] Page tests cover the error alerts, both state alerts, the risks box with its count, and a Service with no risks box.
- [ ] You try the risks box across a refresh by hand on `mise run dev:up`.
- [x] `docs/user-guide.md` describes the alerts and the risks box.
