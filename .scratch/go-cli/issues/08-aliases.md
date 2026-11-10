# 08: Aliases

Status: done

Blocked by: 05

Spec: `../spec.md`.

## What to build

A Service can use the Instances of a Remote Service.

- `alias add`: links a Service to a Remote Service. Adding an Alias is safe to repeat, and it says whether it created the Service or the Alias. See `Alias.java`.
- `alias list` and `alias remove`.
- The client methods and schemas that these need.

Port the cases of `AliasTest` that test CLI behavior.

## Acceptance criteria

- [ ] On a real test Yard with two Workstations, an Alias to a Service of the other Workstation gives the Service its Instances.
- [ ] `alias add` twice does not fail, and the text says what changed.
- [ ] `alias list` and `alias remove` work.
- [ ] `mise run check` passes, and the openapi test knows the new calls.
