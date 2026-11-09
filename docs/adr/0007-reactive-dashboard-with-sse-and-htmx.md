---
status: accepted
---

# Reactive dashboard with Server-Sent Events and HTMX

The dashboard uses Server-Sent Events (SSE) to notify open pages when LVH Yard Status, Instance Health, Services, or Containers change. Instead of polling every 5 seconds, each page opens a single SSE connection to `/events`, receives signal events, and re-reads only its refreshed region with HTMX. A slow 30-second poll runs alongside the SSE trigger so the page recovers if a connection drops.

## Considered Options

- **Polling every 5 seconds (the previous design)**: rejected. Every open tab issued periodic HTTP requests against Consul and Docker whether anything changed or not, and changes took up to 5 seconds to appear on screen.
- **Pushing rendered HTML fragments directly over SSE**: rejected. The server would need to track client-side query parameters (such as search filters on the Catalog page) or push out-of-band updates for individual table rows. Signal-only SSE lets HTMX make standard GET requests with existing filters, keeping HtmlFlow views and controllers stateless and simple.
- **Watching remote Consul datacenters with blocking queries over WAN**: rejected. When a remote Workstation disconnects, cross-WAN blocking queries hold threads, time out, and trigger circuit breakers. Watching only the local Consul agent and its WAN pool member list keeps blocking queries on loopback. Remote Workstation changes update through the 30-second fallback poll.
- **WebSockets**: rejected. Full duplex communication is unnecessary because user actions already submit via standard HTTP POST and GET. SSE works over plain HTTP, reconnects automatically, and works with Traefik out of the box.

## Consequences

- The header `<body>` opens one SSE stream at `/events` using the `hx-sse` extension.
- Pages update within milliseconds when containers start, stop, or health checks change.
- The server debounces bursts within 300 ms to avoid multiple rapid GET requests during bulk operations.
- Reachability testing remains strictly on demand and does not emit or listen to SSE events.
- Traefik keeps the connection open because the dashboard emits a comment heartbeat (`: ping`) every 15 seconds.
