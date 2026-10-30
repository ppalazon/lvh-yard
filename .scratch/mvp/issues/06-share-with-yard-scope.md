# 06: Share a Service with Yard Scope

**What to build:** A developer sets a Publication's Scope to Yard, and every other Workstation in the Yard reaches the Service at `<service>.<workstation>.<base-domain>`. Routes to a Workstation that stops answering are dropped and come back with it. A Yard-scoped Service warns when one of its Instances is on a loopback address.

**Blocked by:** 03 (Publish a Service on its own Workstation), 05 (See Remote Services across the Yard)

**Status:** done

- [x] A Publication can have Local or Yard Scope. New Publications have Local Scope.
- [x] The Scope can be changed from the Service detail page, the API and `yard publish --scope`.
- [x] Every other Workstation's Traefik configuration has a Route for `<service>.<workstation>.<base-domain>` to the Instances of each Remote Service with Yard Scope.
- [x] A Remote Service with Local Scope gets no Route on other Workstations.
- [x] The Home Workstation keeps its short hostname `<service>.<base-domain>` whatever the Scope.
- [x] Routes to an unreachable Workstation's Services are dropped, and come back by themselves when it answers again.
- [x] A Yard-scoped Service with an Instance on a loopback address shows a warning on its detail page and in the API.
