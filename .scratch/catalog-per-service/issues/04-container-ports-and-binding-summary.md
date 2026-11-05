# 04: Container ports on the Instances card, and a Container Binding summary

**What to build:** A Bound Instance knows its container name, Container Port and Host Port. The API sends them, and the Instances card shows them. The Container Bindings card shows a summary for each Binding in place of the list of addresses. See `.scratch/catalog-per-service/spec.md`, user stories 15 to 22 and 33.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] A Bound Instance stores its container name and Container Port in its Consul meta.
- [ ] The API `Instance` has `container`, `containerPort` and `hostPort`. A Static Instance has none of them. A Bound Instance with no Host Port has no `hostPort`.
- [ ] The Instances card columns are Instance, Container, Container Port, Host Port, Health, then the actions.
- [ ] A Static Instance has empty container cells. A Bound Instance with no Host Port shows "Not published".
- [ ] A Remote Service shows the same columns.
- [ ] Each Container Binding row says "N containers, P passing", or "1 container, P passing".
- [ ] A Binding with no match keeps today's message.
- [ ] `docs/user-guide.md` describes the new columns and the summary.
- [ ] Seam 1 tests cover a Compose service with several containers, a Bound Instance with and without a Host Port, a Static Instance, and the OpenAPI fields.
