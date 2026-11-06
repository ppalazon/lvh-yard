# 03: Reactive Catalog and Service pages via Consul watcher

**What to build:** A background Consul watcher runs blocking queries on a dedicated virtual thread against the local Consul agent, monitoring Instance Health (`/v1/health/state/any`), KV registrations and publications (`/v1/kv/lvh-yard/?recurse`), and WAN pool membership (`/v1/agent/members?wan=1`). When an Instance check fails or passes, or when a Service or Publication changes, the watcher notifies the debouncer, which broadcasts a `yard-changed` signal over SSE. The Catalog and Service pages refresh their cards and tables immediately via HTMX partial swap without 5-second polling. Active Catalog filters and Service risk disclosure box states are preserved across updates, while Reachability testing remains strictly on demand.

**Blocked by:** 01: SSE stream foundation and keepalive

**Status:** done

- [x] A Consul watcher running as a Spring `SmartLifecycle` component executes blocking queries against the local Consul agent on a virtual thread.
- [x] When Consul is down or restarting, the watcher logs a single warning, pauses with fixed backoff, and reconnects cleanly once Consul answers.
- [x] Changes to Instance Health checks, Service registrations, Publications, Aliases, Route Profiles, or WAN pool members trigger a debounced `yard-changed` signal over SSE.
- [x] The Catalog page refreshed region uses `hx-trigger="sse:yard-changed, every 30s"` and updates its rows immediately when an Instance changes Health.
- [x] Active text and state filter parameters in the Catalog are preserved across reactive updates.
- [x] The Service page refreshed region uses `hx-trigger="sse:yard-changed, every 30s"` and updates summary cards, Instance tables, and Container Bindings immediately.
- [x] Open or closed risk disclosure boxes on the Service page retain their state across reactive updates.
- [x] The Reachability test section on the Service page does not listen to or trigger on SSE events.
- [x] Black-box tests verify that registering an Instance or changing a check emits `yard-changed` and updates the Catalog row and Service page.
