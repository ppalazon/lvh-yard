# 04: Reactive Status dots and Status page

**What to build:** When an infrastructure Status Part (Consul, Traefik, Docker) transitions between OK, warning, or failing, the dashboard broadcasts a `status-changed` signal over the `/events` SSE stream. The header navigation bar status summary dots (`#status-dots`) and the `/status` page listen for this signal and refresh immediately via HTMX partial swap, replacing 5-second polling with event-driven updates alongside a 30-second fallback poll.

**Blocked by:** 01: SSE stream foundation and keepalive, 03: Reactive Catalog and Service pages via Consul watcher

**Status:** done

- [x] Status transitions in Consul, Traefik, or Docker trigger a debounced `status-changed` signal over the `/events` SSE stream.
- [x] The status summary dots in the navigation bar use `hx-trigger="load, sse:status-changed, every 30s"`.
- [x] The Status page refreshed region uses `hx-trigger="sse:status-changed, every 30s"`.
- [x] When an infrastructure part fails or recovers, the header dots update their color and status text without a manual page reload.
- [x] Black-box tests verify that an infrastructure status transition emits `status-changed` on the SSE stream and updates the status dots.

## Comments

Implemented in `369af63`, merged in `540a0ec`. Code review adjustments merged in `69e2272`.
