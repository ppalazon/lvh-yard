# 07: Publications and Instance Priority

Status: done

Blocked by: 05

Spec: `../spec.md`.

## What to build

A user can give a Service a hostname, take it away, and choose the order of its Instance Sources.

- `publish`: makes a Publication with a Scope (Local or Yard), with the choice of whether Traefik logs the requests, and with Route Profiles. Publishing again with no Scope keeps the Scope. See `Publish.java`.
- `unpublish`: removes only the Publication.
- `priority`: shows and sets the Instance Priority, with its order of Instance Sources, and turns it on or off. See `Priority.java`.
- The client methods and schemas that these need.

Port the cases of `PublishTest` that test CLI behavior, and the cases of the Instance Priority that the old `ProfilesTest` or `CatalogTest` hold.

## Acceptance criteria

- [ ] On a real test Yard, a published Service gets its hostname, and `unpublish` removes it while the Service and its Instances stay.
- [ ] `priority` shows the order, and a change shows in `catalog --details`.
- [ ] `mise run check` passes, and the openapi test knows the new calls.
