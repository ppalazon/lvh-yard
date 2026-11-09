# 03: Aliases belong to a Service

**What to build:** On the Service page, you add Aliases to Remote Services. Each Alias is an Instance Source in the order. See `.scratch/service-workflow/spec.md`, user stories 35 to 41, 51, 56, 57, 67 and 68.

**Blocked by:** 02

**Status:** done

- [x] An Alias is stored with its Service. Its source ID is `<workstation>/<service>` of its Remote Service.
- [x] A Service can have many Aliases. A second Alias to the same Remote Service is refused, and so is an Alias to a Service of this Workstation.
- [x] Only a Remote Service with Yard Scope can be the target of a new Alias.
- [x] After its target leaves Yard Scope or its Workstation becomes Unreachable, an Alias stays. It then gives no Instances, and the page says why.
- [x] Only the Home Workstation's Route uses Alias sources. Other Workstations skip them in the order.
- [x] With the toggle off, the passing Instances of Aliases share the Route too.
- [x] A Service of Yard Scope with an Alias source above another source has a risk Service Warning.
- [x] The REST API adds and removes an Alias of a Service.
- [x] The Service page has an "Add Alias" form that lists the Remote Services with Yard Scope. Each Alias shows the Instances of its Remote Service.
- [x] `lvh-yard alias add <service> --workstation <w> --service <s>`, `alias remove` and `alias list` work per Service.
- [x] The Workstation-level Aliases API, the short Alias hostnames and the Aliases page go away. The menu loses the Aliases link.
- [x] Unit and component tests cover each rule above, including the remote Workstation fake. CLI tests cover the `alias` commands. The OpenAPI test covers the changed endpoints.
- [x] If 05 and 06 are done, the Service page parts follow the order of the spec: summary, Publication, Instance Sources, Route Profiles, Reachability and Delete.
- [x] `docs/user-guide.md` explains Aliases on the Service page.
