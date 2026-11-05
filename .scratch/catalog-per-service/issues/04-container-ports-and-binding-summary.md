# 04: Container ports on the Instances card, and a Container Binding summary

**What to build:** A Bound Instance knows its container name, Container Port and Host Port. The API sends them, and the Instances card shows them. The Container Bindings card shows a summary for each Binding in place of the list of addresses. See `.scratch/catalog-per-service/spec.md`, user stories 15 to 23 and 34.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] A Bound Instance stores its container name and Container Port in its Consul meta.
- [x] The API `Instance` has `container`, `containerPort` and `hostPort`. A Static Instance has none of them. A Bound Instance with no Host Port has no `hostPort`. A container on the host network has its Container Port as `hostPort`.
- [x] The Instances card columns are Instance, Container, Container Port, Host Port, Health, then the actions.
- [x] A Static Instance has empty container cells. A Bound Instance with no Host Port shows "Not published".
- [x] A Remote Service shows the same columns.
- [x] Each Container Binding row says "N containers, P passing", or "1 container, P passing".
- [x] A Binding with no match keeps today's message.
- [x] `docs/user-guide.md` describes the new columns and the summary.
- [x] Seam 1 tests cover a Compose service with several containers, a Bound Instance with a mapped Host Port, one on the host network and one with no Host Port, a Static Instance, and the OpenAPI fields.

## Comments

Implemented in `9918a80`, merged in `23848a1`. The integration check passed, including remote and OpenAPI tests. Real Docker and API smoke checks covered mapped, unmapped and host-network ports. Browser checks showed the Binding summary, the new columns, Not published and empty container cells for a Static Instance. Standards and Spec reviews found no issues.
