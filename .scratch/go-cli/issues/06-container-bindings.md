# 06: Container Bindings

Status: done

Blocked by: 05

Spec: `../spec.md`.

## What to build

A user can see the containers of this Workstation and make a Container Binding to one.

- `containers`: lists the containers, running or stopped, and marks each Unfollowed Container. See `Containers.java`.
- `register container` and `register compose`: make a Container Binding by container name, or by Compose project and service, and a Container Port. Several Services can bind the same container or Compose service. See `RegisterContainer.java` and `RegisterCompose.java`.
- The client methods and schemas that these need, including the Host Port of a container port.

Port the cases of `ContainersTest` and the container part of `RegisterTest` that test CLI behavior.

## Acceptance criteria

- [ ] On a real test Yard, a started container shows as an Unfollowed Container, and after `register container` it has a Bound Instance.
- [ ] A Compose binding follows each container of the Compose service.
- [ ] Two Services can bind the same container.
- [ ] `mise run check` passes, and the openapi test knows the new calls.
