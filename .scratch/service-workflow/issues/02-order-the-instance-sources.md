# 02: Order the Instance Sources

**What to build:** On the Service page, you put the Instance Sources of a Service in order, and you switch Instance Priority on or off. The Route follows the order, on this Workstation and on every other one. See `.scratch/service-workflow/spec.md`, user stories 16, 42 to 55 and 69 to 71, and ADR-0009.

**Blocked by:** 01

**Status:** done

- [x] The Service record holds the order of Instance Source IDs and the toggle. The Publication keeps only its Scope, Route Profiles and access log.
- [x] The order and the toggle stay over unpublish and publish.
- [x] A Static Instance and a Foreign Instance use their Consul ID as source ID. A Container Binding uses its Binding ID.
- [x] A new source and a new Foreign Instance go to the end. An ID whose source is gone is skipped at read time and dropped at write time.
- [x] With the toggle on, the Route uses the passing Instances of the first source that has one. If none passes, it uses every Instance in range.
- [x] With the toggle off, every passing Instance of every source shares the Route.
- [x] Out-of-range Instances never count.
- [x] The routing result in the API names the source that the Route uses now and the sources that wait.
- [x] The REST API reads and sets the order and the toggle.
- [x] The Service page lists the sources in order, each with its Instances, with up and down buttons and the toggle. The buttons hide while the toggle is off. The page marks the source that the Route uses now.
- [x] A Remote Service page shows the ordered sources without buttons.
- [x] `lvh-yard priority <service>` prints the sources in order with their IDs. `lvh-yard priority <service> <id>...` sets the order, and `--on` and `--off` switch the toggle. `lvh-yard publish` loses `--priority`.
- [x] The InstanceKind order and its default go away.
- [x] A unit test of the Route choice covers each edge case of the spec.
- [x] Component tests cover the API, the page and a Yard Service read from the remote Workstation fake. CLI tests cover `priority`.
- [x] `InstancePriorityIT` shows through a real Traefik that the Route moves to the second Static Instance after the first one stops.
- [x] `docs/user-guide.md` explains the order and the toggle.
