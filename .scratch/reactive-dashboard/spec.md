# Reactive dashboard with Server-Sent Events and HTMX

Status: done

Words follow `GLOSSARY.md`. Architectural decisions follow `docs/adr/0007-reactive-dashboard-with-sse-and-htmx.md`.

## Problem Statement

The dashboard relies on blind 5-second polling across all open pages (`Layout.refreshed` and `Layout.statusDots`). Every open browser tab repeatedly polls the server, triggering full reads of Consul and Docker even when nothing has changed.

From the developer's perspective:

- When a container starts, stops, or an Instance health check fails, the dashboard takes up to 5 seconds to reflect the change.
- Polling the full Catalog and Service views every 5 seconds creates unnecessary background load on Consul and Docker.
- In multi-tab workflows, polling from several tabs compounds server requests and can cause intermittent UI flickers as full sections are swapped blindly on fixed timers.

## Solution

The dashboard replaces continuous 5-second polling with reactive Server-Sent Events (SSE) using HTMX's `hx-sse` extension.

A single SSE connection per browser tab at `/events` delivers lightweight, typed event signals (`yard-changed`, `containers-changed`, `status-changed`) when backend state changes:

- `ContainerWatcher` broadcasts container events when Docker reports container lifecycle changes.
- A dedicated Consul watcher uses HTTP blocking queries on the local Consul agent to detect changes to Instance Health, service registrations, and WAN pool membership immediately.
- Rapid bursts of events (such as multiple containers starting in parallel) are debounced within a 300 ms quiet window.
- The browser views subscribe to specific signals with `hx-trigger="sse:<event>, every 30s"`. When a signal arrives, HTMX fetches only the updated partial view using standard GET requests, preserving client filters and form states.
- A slow 30-second fallback poll runs alongside the SSE trigger to guarantee eventual consistency if an event is missed or a connection temporarily drops.
- A 15-second comment heartbeat (`: ping`) keeps proxy connections open through Traefik and enables timely cleanup of dead sockets.

## User Stories

1. As a developer viewing the Catalog, I want the list of Services to refresh immediately when an Instance's Health changes, so that I see whether a Service is Healthy, Degraded, or Unhealthy without delay.
2. As a developer viewing the Catalog, I want the list of Services to update automatically when a Service is published or unpublished, so that the Hostname and State columns always match reality.
3. As a developer viewing the Catalog, I want new Service registrations to appear immediately, so that newly registered Services are visible without manual page reloads.
4. As a developer viewing the Catalog with a text or state filter active, I want reactive updates to preserve my active filter, so that background events do not reset what I am inspecting.
5. As a developer viewing a Service page, I want the summary cards and Instance tables to refresh immediately when an Instance check fails or passes, so that I can observe live health transitions during debugging.
6. As a developer viewing a Service page, I want the Container Bindings section to update immediately when matching containers start or stop, so that I see the current Bound Instances in real time.
7. As a developer viewing a Service page, I want open or closed risk disclosure boxes to keep their state across reactive refreshes, so that incoming events do not interrupt my reading.
8. As a developer viewing a Service page, I want Reachability tests to remain strictly on demand, so that background events do not trigger unwanted network requests through Traefik or to container ports.
9. As a developer viewing the Containers page, I want Unfollowed Containers to update immediately when a new container starts, so that I can create a Container Binding right away.
10. As a developer viewing the Containers page, I want stopped containers to disappear immediately from the Unfollowed Containers list, so that the list only reflects running containers.
11. As a developer filling out a Container Binding form, I want the form to sit outside the refreshed region, so that an incoming container event does not clear what I am typing.
12. As a developer looking at any page, I want the Status summary dots in the header navigation to update immediately when Consul, Traefik, or Docker changes status, so that I know about infrastructure problems as soon as they happen.
13. As a developer viewing the Status page, I want the Status Parts list to refresh immediately when a part becomes warning or failing, so that I can read the reason without refreshing the browser.
14. As a developer working with multiple browser tabs open, I want each tab to maintain only a single SSE connection to the dashboard, so that I do not exhaust browser HTTP connection limits.
15. As a developer starting multiple containers in parallel with Compose, I want rapid lifecycle events debounced, so that my browser does not issue dozens of simultaneous page reloads.
16. As a developer whose Consul agent briefly restarts, I want the Consul watcher to retry with backoff and reconnect cleanly, so that live updates resume automatically once Consul answers.
17. As a developer whose browser tab sits idle behind Traefik, I want regular keepalive heartbeats on the event stream, so that intermediate proxies do not silently kill the stream.
18. As a developer whose laptop wakes from sleep, I want a 30-second fallback poll alongside the SSE trigger, so that my page catches up with any state changes missed while asleep.
19. As a developer in a multi-Workstation Yard, I want the local Consul watcher to detect WAN pool membership changes, so that joining or leaving Workstations trigger Catalog refreshes without hanging cross-WAN queries.
20. As a developer browsing on a low-power device, I want quiescent dashboard pages to consume zero CPU when no events occur, so that unnecessary polling overhead is eliminated.
21. As a developer using keyboard navigation or screen readers, I want partial DOM updates triggered by signals rather than full-page refreshes, so that my cursor focus is not abruptly reset.
22. As a developer running LVH Yard without internet access, I want the SSE JavaScript assets served locally from WebJars, so that real-time reactivity functions completely offline.
23. As a developer navigating between dashboard sections, I want the navigation bar and layout shell to manage the SSE connection transparently, so that transitioning between pages feels seamless.
24. As a developer inspecting network activity in browser developer tools, I want clear and descriptive SSE event names, so that I can easily trace which subsystem triggered a UI update.
25. As an operator running LVH Yard under load, I want disconnected SSE clients to be cleaned up promptly on heartbeat failures, so that the dashboard server does not leak memory or thread handles.

## Implementation Decisions

- **Event Delivery Pattern**: Use signal-only SSE events (`event: <name>\ndata:\n\n`) where the server notifies the client of state changes, and HTMX fetches the refreshed HTML partial using standard GET requests. No HTML fragments or out-of-band DOM swaps are pushed over the event stream.
- **Connection Topology**: Every HTML page shares a single SSE connection established on the `<body>` element via `hx-ext="sse"` and `sse-connect="/events"`. Child elements subscribe to specific signals with `hx-trigger="sse:<event>, every 30s"`.
- **Event Broadcaster**: An in-memory broadcaster manages active client `SseEmitter` subscriptions. It handles client registration, completion on disconnect, and scheduled comment heartbeats (`: ping`) every 15 seconds.
- **Event Types**: The broadcaster defines three canonical signal types:
  - `yard-changed`: Emitted when Service registrations, Publications, Instance Health, Container Bindings, or Aliases change.
  - `containers-changed`: Emitted when Docker containers start, stop, or change state.
  - `status-changed`: Emitted when LVH Yard Status Parts (Consul, Traefik, Docker) change state.
- **Debouncing**: An event debouncer coalesces rapid event triggers across Docker and Consul into a 300 ms quiet window before broadcasting signals, avoiding refresh storms during parallel container or health transitions.
- **Consul Watcher**: A Spring `SmartLifecycle` component running on a dedicated virtual thread (`consul-watcher`) uses Consul HTTP blocking queries (`?index=<index>&wait=60s`) on the local Consul agent:
  - Watches `/v1/health/state/any` for Instance Health transitions.
  - Watches `/v1/kv/lvh-yard/?recurse=true` for Publication, Binding, Profile, and Alias changes.
  - Watches `/v1/agent/members?wan=1` for WAN pool changes.
  - When Consul is unavailable or restarting, it logs a single warning and retries with a fixed backoff pause, mirroring the resilience pattern of `ContainerWatcher`.
- **Docker Watcher Enhancement**: `ContainerWatcher` notifies the event debouncer on container events in addition to syncing bindings.
- **Refreshed View Contract**: The `refreshed` layout helper is updated to accept an event name parameter, rendering `hx-trigger="sse:<event>, every 30s"`:
  - Catalog page (`#catalog`): `sse:yard-changed, every 30s`
  - Service page (`#service`): `sse:yard-changed, every 30s`
  - Containers page (`#containers`): `sse:containers-changed, sse:yard-changed, every 30s`
  - Status page (`#status`): `sse:status-changed, every 30s`
  - Status summary dots (`#status-dots`): `load, sse:status-changed, every 30s`
- **Reachability Separation**: Reachability remains strictly on demand per `GLOSSARY.md`. It does not listen to or emit SSE events.

## Testing Decisions

- **Single Test Seam**: All tests continue to target the single highest seam in the repository: `DashboardTest` (running the Spring Boot application as a black box with a real Consul container and Docker engine).
- **External Behavior Only**: Tests must verify externally observable behavior rather than internal broadcaster or watcher state:
  - Connect to `/events` with an HTTP/SSE client.
  - Trigger domain events (register a service, update a container, alter a health check).
  - Verify that the expected typed event (`yard-changed`, `containers-changed`, or `status-changed`) is received within a bounded timeout.
  - Verify that HTML pages render with the expected `hx-trigger` attributes containing `sse:<event>` and the 30-second fallback.
  - Verify that heartbeats are received on idle connections.
- **Prior Art**:
  - `CatalogPageTest`: Tests page rendering and service state updates against real Consul data.
  - `ContainersTest`: Tests container discovery and binding flows against Docker.
  - `StatusTest`: Tests Status Part aggregation and timeout handling.
  - `DashboardTest`: Provides the shared Testcontainers harness, HTTP client utilities, and async polling helpers via Awaitility.

## Out of Scope

- Pushing raw HTML fragments or out-of-band partials through the SSE stream.
- WebSocket support or bidirectional client-to-server socket communication.
- Cross-WAN Consul blocking queries against remote Workstation datacenters.
- Automated or scheduled Reachability testing over SSE.
- Desktop or browser push notifications.

## Further Notes

- The `htmx.org` 4.0.0 WebJar already packages `dist/ext/hx-sse.min.js`, so no new external client-side dependencies are needed.
- Spring Boot WebMvc includes native `SseEmitter` support, avoiding the need for Spring WebFlux or reactive streaming dependencies.
