# 04: Reactive Status dots and Status page

**What to build:** When an infrastructure Status Part (Consul, Traefik, Docker) transitions between OK, warning, or failing, the dashboard broadcasts a `status-changed` signal over the `/events` SSE stream. The header navigation bar status summary dots (`#status-dots`) and the `/status` page listen for this signal and refresh immediately via HTMX partial swap, replacing 5-second polling with event-driven updates alongside a 30-second fallback poll.

**Blocked by:** 01: SSE stream foundation and keepalive, 03: Reactive Catalog and Service pages via Consul watcher

**Status:** ready-for-agent

- [ ] Status transitions in Consul, Traefik, or Docker trigger a debounced `status-changed` signal over the `/events` SSE stream.
- [ ] The status summary dots in the navigation bar use `hx-trigger="load, sse:status-changed, every 30s"`.
- [ ] The Status page refreshed region uses `hx-trigger="sse:status-changed, every 30s"`.
- [ ] When an infrastructure part fails or recovers, the header dots update their color and status text without a manual page reload.
- [ ] Black-box tests verify that an infrastructure status transition emits `status-changed` on the SSE stream and updates the status dots.
