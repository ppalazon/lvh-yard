# 05: Join a mixed-platform Yard

**What to build:** A Windows Workstation joins a Yard with Linux or macOS Workstations. Members discover each other's Services and route Yard Publications backed by LAN-reachable Static Instances in both directions.

**Blocked by:** 03: Set up a private local Windows Workstation.

**Status:** done

- [x] Pair a real Windows 11 Workstation with at least one Linux or macOS Workstation on the same LAN. Joining and peer discovery work with Windows as both the joining and existing Workstation.
- [x] Consul advertises a Windows LAN address reachable by the other Workstation. Its runtime bind address and default Instance host retain their separate meanings.
- [x] Multiple adapters and an explicit LAN override do not silently advertise a VPN, VM-only address or host-relative Docker hostname.
      Observed 2026-11-19 on real Windows with a second network card ([evidence](../evidence/03-05-multiple-adapters.md)): a tunnel-named adapter on the default route is refused with `Use --address`; an explicit `--address` is written as given, including the address of the other adapter, and no hostname is chosen. The setup does not check that an explicit address belongs to this computer. The tunnel adapter was a renamed card, not a real VPN.
- [x] Register a LAN-reachable Static Instance on Windows, publish it with Yard Scope, and successfully request it through the other Workstation's Route.
- [x] Publish a Service on the other platform and successfully request that Remote Service through the Windows Workstation. Verify registered endpoints, Health and actual responses.
- [x] Preserve local-only listener defaults and existing Out-of-range Instance handling. Joining a Yard must not silently expose local dashboard or admin interfaces.
- [x] Document the required Consul federation firewall access and address-selection limits. Do not change firewall rules automatically or promise native interface binding Docker Desktop cannot provide.
- [x] Record real LAN federation and request evidence. Container-based simulated LAN forwarding cannot satisfy this ticket, and a failed path cannot be replaced by reduced Yard support.

Evidence: [05-join-mixed-platform-yard.md](../evidence/05-join-mixed-platform-yard.md). It records one defect fixed here
(Consul bound to an address Docker Desktop does not forward), the real ambiguity limit of the one-VPN-free VM, and
cross-Workstation read stalls seen on the overloaded shared VM that remain undiagnosed.

Accepted limit (maintainer, 2026-11-16): the cross-Workstation read stalls and the Route flapping on the overloaded 2-CPU VM are not diagnosed. On the larger VM
(4 CPUs, 6 GB) with at most two stacks, the passes of `08` and `10` saw no stall. That does not explain the earlier ones.
