# 01: The Degraded state

**What to build:** A published Service with at least one passing Instance and at least one Instance that does not pass is Degraded. Healthy now means that every Instance passes. The Degraded and warning badges use the risk orange. See `.scratch/catalog-per-service/spec.md`, user stories 1 to 9, 32 and 33.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `ServiceState` has `DEGRADED` (`degraded`), between Unhealthy and Healthy.
- [ ] Warning and critical count as not passing. The state counts every Instance and ignores the Instance Priority and `outOfRange`.
- [ ] An Unpublished Service stays Unpublished, whatever its Health.
- [ ] The Catalog State filter and the OpenAPI enum show Degraded.
- [ ] A new badge variant uses the `--risk` orange. Degraded and warning Health use it.
- [ ] `lvh-yard list` prints `degraded`.
- [ ] `docs/user-guide.md` describes Degraded and the new meaning of Healthy.
- [ ] Tests on Seam 1 and Seam 2 cover each case of the spec.
