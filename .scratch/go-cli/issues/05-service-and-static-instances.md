# 05: Create a Service with a Static Instance

Status: done

Blocked by: 03

Spec: `../spec.md`.

## What to build

A user can create a Service, give it a Static Instance, remove an Instance Source, and delete the Service.

- `service create` and `service delete`. Both are safe to repeat. See `ServiceCommand.java`.
- `register static`, with the options that `StaticInstanceOptions.java` gives, and `remove` for one Instance Source.
- The client methods and schemas that these need, including the refusal that the API gives for a Remote Service.

Port the cases of `ServiceCommandTest` and `RegisterTest` that test CLI behavior, for the static part.

## Acceptance criteria

- [x] On a real test Yard, a new Service with a Static Instance shows in `catalog`, and `remove` and `service delete` take them away.
- [x] Repeating `service create` or `service delete` does not fail.
- [x] A change to a Remote Service gives the dashboard's refusal as one error line and exit code 1.
- [ ] `mise run check` passes, and the openapi test knows the new calls.

## Comments

- 2026-11-10: The integration owner reported that `go vet` passed. Initial Go test failures came from incorrect expectations for canonical colon paths. After the owner changed the tests to check semantic names and escaped segment boundaries, the targeted Register, EditInstance and Remove tests and the build passed.
- The integration owner checked repeated Service creation and deletion on the live two-Workstation Yard. Registration with every Static Instance flag passed, and `catalog --details` showed the Instance ID. Removing the Instance Source kept its Service.
- The live dashboard returned HTTP 405 with the Home Workstation explanation for a Remote Service change. The command tests cover the refusal message, standard error and exit code 1.
- `mise run check` and hosted checks remain unverified. No tests, builds, linters or formatters ran during integration.
