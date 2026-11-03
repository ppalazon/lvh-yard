# 03: Change the Instance Priority of a Service

**What to build:** The user sets `lvh-yard publish web --priority bound,static` (or edits the order on the Service page), and every Workstation routing `web` uses that order.

**Blocked by:** 01

**Status:** done

- [x] `PublicationRequest` takes `priority`; absent keeps the current order, as with `scope`
- [x] Unknown or repeated kinds are refused with a reason; a kind can be left out
- [x] The order is stored with the Publication; Publications without one use the default
- [x] The Service page edits the order (move up, move down, remove)
- [x] `lvh-yard publish --priority` sends it; refusals exit 1
