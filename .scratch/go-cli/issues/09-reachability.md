# 09: Reachability

Status: ready-for-agent

Blocked by: 03

Spec: `../spec.md`.

## What to build

`lvh-yard test` shows the Reachability of a Service's Instances, directly and through its Route, as `TestReachability.java` does. It works for a Service of this Workstation and for a Remote Service. It skips an Out-of-range Instance of a Remote Service and says so.

Port the cases of `TestReachabilityTest` that test CLI behavior.

## Acceptance criteria

- [ ] On a real test Yard, a passing Instance and a stopped one show different results.
- [ ] An Out-of-range Instance is skipped with a reason.
- [ ] `mise run check` passes, and the openapi test knows the new calls.
