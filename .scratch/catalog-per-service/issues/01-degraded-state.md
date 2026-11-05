# 01: The Degraded state

**What to build:** A published Service with at least one passing Instance and at least one Instance that does not pass is Degraded. Healthy now means that every Instance passes. The Degraded and warning badges use the risk orange. See `.scratch/catalog-per-service/spec.md`, user stories 1 to 9, 32 and 33.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `ServiceState` has `DEGRADED` (`degraded`), between Unhealthy and Healthy.
- [x] Warning and critical count as not passing. The state counts every Instance and ignores the Instance Priority and `outOfRange`.
- [x] An Unpublished Service stays Unpublished, whatever its Health.
- [x] The Catalog State filter and the OpenAPI enum show Degraded.
- [x] A new badge variant uses the `--risk` orange. Degraded and warning Health use it.
- [x] `lvh-yard list` prints `degraded`.
- [x] `docs/user-guide.md` describes Degraded and the new meaning of Healthy.
- [x] Tests on Seam 1 and Seam 2 cover each case of the spec.

## Comments

Implemented in `33615cb`, merged in `afad226`. The integration check passed. Browser and CLI smoke checks showed Degraded for mixed Health, the State filter, the orange badge and Instance IDs in `lvh-yard list`. Standards review found no issues.
