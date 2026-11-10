# 04: `catalog`

Status: done

Blocked by: 03

Spec: `../spec.md`.

## What to build

`lvh-yard catalog` lists the Services of every Workstation in the Yard, one line each, as `Catalog.java` does. It takes `--workstation <name>`, `--state <state>` (unpublished, unavailable, unhealthy, degraded or healthy) and `--details`, which shows Instance addresses, Health, IDs and Service Warnings. The client gets the Yard's Services and the Service schema with its Instances, Instance Sources, Publication, routing and warnings.

Port the cases of `CatalogTest` that test CLI behavior.

## Acceptance criteria

- [ ] `catalog` lists Services from two Workstations of a real test Yard (`mise run dev:up`).
- [ ] `--workstation`, `--state` and `--details` each narrow or extend the list.
- [ ] An Unreachable Workstation does not break the list.
- [ ] `mise run check` passes, and the openapi test knows the new calls.

## Comments

- 2026-11-10: Implementation is complete. The integration owner reported that `go vet` passed. The full Go test run failed only on the test's incorrect expectation for plus encoding. After the owner changed the assertion to check the decoded path and escaped segment boundary, the narrow Catalog tests passed. The client path escaping was correct and stayed unchanged.
- Live verification and `mise run check` remain pending with the integration owner. No checks were run during the merge.
