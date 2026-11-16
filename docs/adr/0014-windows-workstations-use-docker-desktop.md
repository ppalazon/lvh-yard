---
status: accepted, in part superseded by ADR-0015
---

# Windows Workstations use Docker Desktop as their runtime boundary

The Windows support specification requires a native CLI and a full Workstation in a mixed-platform Yard. Keep Consul, Traefik and the dashboard on Docker Desktop's host network, while treating Windows LAN identity separately from container binding.

This is a Windows extension to [ADR-0012](0012-services-keep-host-networking.md), with a Windows storage exception to [ADR-0011](0011-consul-files-traefik-flags-and-owned-files.md). Existing platform decisions remain unchanged. The maintainer accepted it on 2026-11-16, before every release gate closed. The section "Acceptance, 2026-11-16" lists what stays open.

## Context

The current host-network design lets Traefik reach loopback processes and containers on other Docker networks. Docker Desktop places a runtime boundary between those containers and Windows interfaces.

[Docker's host-network documentation](https://docs.docker.com/engine/network/drivers/host/#docker-desktop), read on 2026-11-13, supports host-loopback communication. It also states that containers cannot bind the native computer's interface addresses.

The specification requires both native-loopback routing and Container Bindings without published ports. Consul advertisement and registered Instance addresses must also remain meaningful to other Workstations.

## Decision

Separate container binding, the Workstation's advertised LAN address and the default Instance host. A bind address must name an address available to the container runtime. Advertisement and Yard-visible Instances must use an address reachable by the other Workstations. The Instance host has its own setting, `instance-host` (`YARD_INSTANCE_HOST`), which defaults to the advertised LAN address.

Use platform-appropriate Compose setup while preserving direct Docker Compose use and user ownership of configuration files. Windows must not depend on a CLI wrapper to repair its runtime environment at launch.

Keep configuration, certificates and exported Backups in the Windows Workstation Folder. Use a named Docker volume for Consul state so its Linux ownership does not depend on NTFS bind-mount identity mapping. The generated `.env` sets `COMPOSE_PROJECT_NAME` from the folder, so each Workstation Folder owns one Consul volume. Initialize volume ownership separately and keep the long-lived runtime processes non-root.

On Windows, Consul starts with `-bind=0.0.0.0` and advertises the LAN address in `YARD_BIND_ADDRESS`, because Docker Desktop forwards only wildcard and loopback listeners of a container to the computer. A bind to the address of the Docker virtual machine was not reachable from the LAN. So Consul listens on every adapter, and `bind-address` sets only the advertised address. The Windows Firewall rules decide who can reach Consul. That includes Docker's own inbound rules, which on the test computer allowed every port that a container listens on at `0.0.0.0`.

Protect Windows files with ACLs, preserving Docker's required access. Do not represent Unix permission modes as an effective Windows privacy boundary.

## Considered Options

- Keep the Linux Compose setup unchanged. Its strength is one configuration model, but it conflates container binding with the Windows LAN identity and relies on Unix ownership assumptions.
- Use a bridge network and require published container ports. Explicit port mapping would simplify the runtime boundary, but requiring publication would reduce the routing contract approved in the specification.
- Run the CLI entirely inside WSL. It would reuse more Unix process behavior, but it would not provide the requested native Windows CLI experience.
- Bind-mount Consul data from NTFS. The files would remain visible beside configuration, but their Linux ownership would need to work through Desktop's filesystem mapping. The volume choice accepts separate state management instead.

## Consequences

Windows has a distinct setup path, an ACL implementation and persistent state outside the Workstation Folder. Normal lifecycle operations preserve that state; destructive reset needs explicit volume handling.

Host networking imposes the Docker prerequisites owned by the specification. It does not establish LAN federation, safe listener exposure or access to unpublished containers. The implementer must supply Windows evidence for each path before acceptance.

The runtime must preserve separate certificate mounts from [ADR-0013](0013-one-yard-ca-and-two-certificates-per-workstation.md). A networking or storage change must not expose Consul's identity key to Traefik.

The CLI gains one dependency, `golang.org/x/sys`, for Windows file ACLs. Only files that build on Windows import it.

If the runtime fails the specification's release gates, the maintainer must reconsider this decision. There is no approved fallback that silently removes routing behavior or Yard participation.

## IPv4-only Windows runtime, 2026-11-14

The maintainer approved IPv4-only Windows Workstation runtime support after
the native Java socket measurements. The generated Windows setup uses
`-Djava.net.preferIPv4Stack=true`. Linux and macOS keep their existing
endpoint-family behavior.

In the actual Docker Desktop 4.99.0 host-network container, curl reached the
IPv4 Consul and Traefik listeners. Java's default TCP and HTTP clients failed
against the same listeners. With the property, Java connected and received
HTTP 200 from Consul.

The cost is explicit: Java IPv6 sockets are unavailable. A separate IPv6
loopback probe returned byte 42 with the unchanged JVM and failed with
`IPv6 protocol family unavailable` under the property. Windows must report
IPv6 Instance targets as unsupported, not offer misleading Reachability or
Health results. This approval does not establish the remaining runtime gates.

Keeping dual-stack support would require replacing or adapting the affected
HTTP and TCP transports. That alternative preserves IPv6 targets but adds
transport code and runtime verification. The approved scope keeps the stock
Java clients and limits Windows to IPv4.

### Java socket measurements

The measurements were observed on 2026-11-14 on Windows 11 Pro x64 (build 26300) as the ordinary user `yarddev`. They used Docker Desktop 4.99.0 (Linux engine 29.9.3, WSL2 backend, host networking enabled). The client was the dashboard image of the Workstation, running as uid 1000 with `--network host`. The target was the Consul HTTP API on `127.0.0.1:49182`, an IPv4 listener.

| Client in the same container   | Command line                                          | Observed result                                                                    |
| ------------------------------ | ----------------------------------------------------- | ---------------------------------------------------------------------------------- |
| curl                           | no Java option                                        | HTTP 200 from the Consul leader URL and the Traefik ping URL.                      |
| Java `Socket`                  | default JVM, `preferIPv4Stack=null`                   | `java.net.ConnectException: Connection refused (connect failed)`.                  |
| Java `HttpClient`              | default JVM                                           | `java.net.ConnectException`, caused by `java.nio.channels.ClosedChannelException`. |
| Java `Socket` and `HttpClient` | `-Djava.net.preferIPv4Stack=true`                     | `Socket connected /127.0.0.1:57848`, then `HTTP 200 "192.168.122.117:49186"`.      |
| Java client, unchanged JVM     | IPv6 loopback listener `[::1]:49192`                  | `IPv6 response=42`.                                                                |
| Java client                    | same listener, with `-Djava.net.preferIPv4Stack=true` | `java.net.SocketException: IPv6 protocol family unavailable`.                      |

The Windows Workstation acceptance record must include these results before
release.

## Acceptance, 2026-11-16

The maintainer accepted this decision on 2026-11-16. Accepting it does not announce Windows support. The release gates below were not closed on that date:

- No release, tag or site deployment has run, so the published installer and ZIP are untested. The `windows` CI job has not run on GitHub.
- Only Docker Desktop 4.99.0 was tested, and the Enhanced Container Isolation setting was not recorded.
- No test had two eligible network adapters. The rule that refuses a VPN or virtual adapter is covered by unit tests only.
- The read stalls and Route flapping seen on a 2-CPU test computer were not diagnosed. They did not appear again on a larger one.
- The second LAN computer in the tests was a Linux host. No macOS Workstation took part.
