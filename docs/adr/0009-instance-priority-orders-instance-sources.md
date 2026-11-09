---
status: accepted
---

# Instance Priority orders Instance Sources

A Service keeps one order of its Instance Sources: each Static Instance, Container Binding, Alias and Foreign Instance. Its Route uses only the first Instance Source in that order with an Instance that passes Health. All passing Instances of that source share the requests. When you turn Instance Priority off, every passing Instance of every source shares the Route, Aliases included. This replaces the order by Instance kind of ADR-0005, because a Service can now have many Aliases and you want to choose between two sources of the same kind. The dashboard still makes the choice from Consul Health each time it builds Traefik's configuration, as in ADR-0005.

## Considered Options

- An order of Instance kinds (ADR-0005): rejected. It cannot choose between two Static Instances, or between two Aliases to different Workstations.
- An order of single Instances: rejected. A Container Binding or an Alias gives Instances that come and go, so the order would not last. An Instance Source lasts until you remove it.

## Consequences

- A new Instance Source goes to the end of the order. A Foreign Instance that appears goes there too.
- Only the Home Workstation's Route uses Aliases. Other Workstations skip Alias sources and go to the next source in the order.
- The order stays stored while Instance Priority is off, so turning it on again brings it back.
