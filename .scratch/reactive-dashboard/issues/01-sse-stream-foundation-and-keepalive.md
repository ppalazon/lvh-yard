# 01: SSE stream foundation and keepalive

**What to build:** When a browser opens any dashboard page, the page shell connects to a single Server-Sent Events (SSE) stream at `/events` using the HTMX `hx-sse` extension. The server hosts an in-memory event broadcaster that tracks active client emitters on virtual threads and sends a comment heartbeat (`: ping`) every 15 seconds to keep intermediate proxy connections alive through Traefik. It also includes an event debouncer that buffers rapid event triggers within a 300 ms window before broadcasting named signals.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] A client connecting to `GET /events` with `Accept: text/event-stream` establishes a persistent SSE connection.
- [ ] Comment heartbeats (`: ping`) are emitted on the SSE stream every 15 seconds.
- [ ] Disconnected clients are detected on heartbeat or write failure and removed without leaking resources or threads.
- [ ] The event debouncer coalesces triggers occurring within 300 ms and flushes distinct event names once the quiet window elapses.
- [ ] The dashboard layout shell includes the `hx-sse` extension and connects to `/events` on the `<body>` element.
- [ ] Black-box tests in the test suite verify that `/events` answers with `text/event-stream` and receives heartbeat comments.
