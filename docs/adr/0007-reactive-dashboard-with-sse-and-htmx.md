---
status: accepted
---

# Reactive dashboard with Server-Sent Events and HTMX

The dashboard tells open pages through Server-Sent Events (SSE) when something changes: the Status, the Health of an Instance, a Service or a container. Each page opens one SSE connection to `/events`. An event carries only a name, such as `yard-changed`. The page then loads its refreshed region again with HTMX. A slow poll every 30 seconds runs beside the events. If a connection drops, the poll still refreshes the page.

## Considered Options

- Polling every 5 seconds, the earlier design: rejected. Each open tab sent requests to Consul and Docker every 5 seconds, with or without a change. A change took up to 5 seconds to show.
- HTML fragments sent over SSE: rejected. For that, the server must know the query of each page, such as the Catalog filters, or send updates for single table rows. With events that carry only a name, HTMX sends a normal GET with the filters of the page. So the views and controllers keep no state.
- Blocking queries to the Consul datacenters of other Workstations: rejected. When a Workstation disconnects, a blocking query over the WAN holds a thread, times out and trips the circuit breaker. The dashboard watches only its own Consul agent and its WAN pool members, so each blocking query stays on loopback. A change on another Workstation shows at the next 30-second poll.
- WebSockets: rejected. Pages need no messages from the browser, because forms already use HTTP POST and GET. SSE uses plain HTTP, reconnects by itself, and works through Traefik with no extra configuration.

## Consequences

- The `<body>` of each page opens the SSE stream at `/events` with the HTMX `hx-sse` extension.
- A page shows a change soon after the event, without waiting for a poll.
- The server waits 300 ms for more triggers before it sends an event, and sends at most 2 seconds after the first trigger. So a burst, such as many containers that start together, gives one event.
- A Reachability test runs only on demand. It sends and listens to no events.
- The dashboard sends a comment (`: ping`) every 15 seconds, so Traefik keeps the connection open.
