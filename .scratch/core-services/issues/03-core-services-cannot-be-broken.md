# 03: Core Services cannot be removed, unpublished or shared

**What to build:** A user cannot break their own Workstation by mistake: changes to a Core Service are refused with a reason, and the page does not offer them.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] Removing a Core Service's Instance, unpublishing it, and Yard Scope answer a client error with a reason (problem details, as for Remote Services)
- [ ] Registering another Service named `yard`, `consul` or `traefik` is refused
- [ ] `lvh-yard remove`, `unpublish` and `publish --scope yard` print the reason and exit 1
- [ ] The Service page of a Core Service hides remove, unpublish and Scope controls
- [ ] The OpenAPI test covers the refusals
