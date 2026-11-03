# 03: Change the Instance Priority of a Service

**What to build:** The user sets `lvh-yard publish web --priority bound,static` (or edits the order on the Service page), and every Workstation routing `web` uses that order.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] `PublicationRequest` takes `priority`; absent keeps the current order, as with `scope`
- [ ] Unknown or repeated kinds are refused with a reason; a kind can be left out
- [ ] The order is stored with the Publication; Publications without one use the default
- [ ] The Service page edits the order (move up, move down, remove)
- [ ] `lvh-yard publish --priority` sends it; refusals exit 1
