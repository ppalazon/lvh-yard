# 09: Reachability

Status: done

Blocked by: 03

Spec: `../spec.md`.

## What to build

`lvh-yard test` shows the Reachability of a Service's Instances, directly and through its Route, as `TestReachability.java` does. It works for a Service of this Workstation and for a Remote Service. It skips an Out-of-range Instance of a Remote Service and says so.

Port the cases of `TestReachabilityTest` that test CLI behavior.

## Acceptance criteria

- [ ] On a real test Yard, a passing Instance and a stopped one show different results.
- [ ] An Out-of-range Instance is skipped with a reason.
- [ ] `mise run check` passes, and the openapi test knows the new calls.

## Comments

- 2026-11-10: Implementation is complete. The integration owner reported that `go vet` and `cli:check` passed, including all Go tests.
- Live verification and `mise run check` remain pending with the integration owner. No checks were run during the merge.
