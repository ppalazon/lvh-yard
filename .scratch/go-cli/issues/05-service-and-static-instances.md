# 05: Create a Service with a Static Instance

Status: ready-for-agent

Blocked by: 03

Spec: `../spec.md`.

## What to build

A user can create a Service, give it a Static Instance, remove an Instance Source, and delete the Service.

- `service create` and `service delete`. Both are safe to repeat. See `ServiceCommand.java`.
- `register static`, with the options that `StaticInstanceOptions.java` gives, and `remove` for one Instance Source.
- The client methods and schemas that these need, including the refusal that the API gives for a Remote Service.

Port the cases of `ServiceCommandTest` and `RegisterTest` that test CLI behavior, for the static part.

## Acceptance criteria

- [ ] On a real test Yard, a new Service with a Static Instance shows in `catalog`, and `remove` and `service delete` take them away.
- [ ] Repeating `service create` or `service delete` does not fail.
- [ ] A change to a Remote Service gives the dashboard's refusal as one error line and exit code 1.
- [ ] `mise run check` passes, and the openapi test knows the new calls.
