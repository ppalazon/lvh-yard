# 02: Reactive Containers page via Docker events

**What to build:** When a Docker container starts, stops, or dies outside the dashboard, the container watcher notifies the event debouncer, which broadcasts a `containers-changed` signal over the `/events` SSE stream. The Containers page listens for this signal and refreshes its Unfollowed Containers table immediately via HTMX partial swap, eliminating the 5-second polling delay while keeping any active form inputs untouched. A 30-second fallback poll runs alongside the trigger so the page recovers if a connection drops.

**Blocked by:** 01: SSE stream foundation and keepalive

**Status:** done

- [x] Docker container lifecycle events trigger a debounced `containers-changed` event over the `/events` SSE stream.
- [x] Multiple containers starting simultaneously (such as during Compose startup) produce a single coalesced `containers-changed` signal.
- [x] The Containers page refreshed container uses `hx-trigger="sse:containers-changed, sse:yard-changed, every 30s"`.
- [x] Starting a container causes the Containers page to show the new Unfollowed Container within milliseconds.
- [x] Partially filled Container Binding forms outside the refreshed list retain their user input across container event updates.
- [x] Black-box tests verify that starting and stopping a container emits `containers-changed` on the SSE stream and updates the rendered HTML.
