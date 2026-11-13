---
status: proposed
---

# Windows Workstations use Docker Desktop as their runtime boundary

The [Windows support specification](../../.scratch/windows-11/spec.md) requires a native CLI and a full Workstation in a mixed-platform Yard. Keep Consul, Traefik and the dashboard on Docker Desktop's host network, while treating Windows LAN identity separately from container binding.

This is a proposed Windows extension to [ADR-0012](0012-services-keep-host-networking.md), with a Windows storage exception to [ADR-0011](0011-consul-files-traefik-flags-and-owned-files.md). Existing platform decisions remain unchanged. Accept this proposal only after the specification's runtime gates pass.

## Context

The current host-network design lets Traefik reach loopback processes and containers on other Docker networks. Docker Desktop places a runtime boundary between those containers and Windows interfaces.

[Docker's host-network documentation](https://docs.docker.com/engine/network/drivers/host/#docker-desktop), read on 2026-11-13, supports host-loopback communication. It also states that containers cannot bind the native computer's interface addresses.

The specification requires both native-loopback routing and Container Bindings without published ports. Consul advertisement and registered Instance addresses must also remain meaningful to other Workstations.

## Decision

Separate container binding, the Workstation's advertised LAN address and the default Instance host. A bind address must name an address available to the container runtime. Advertisement and Yard-visible Instances must use an address reachable by the other Workstations.

Use platform-appropriate Compose setup while preserving direct Docker Compose use and user ownership of configuration files. Windows must not depend on a CLI wrapper to repair its runtime environment at launch.

Keep configuration, certificates and exported Backups in the Windows Workstation Folder. Use a named Docker volume for Consul state so its Linux ownership does not depend on NTFS bind-mount identity mapping. Initialize volume ownership separately and keep the long-lived runtime processes non-root.

Protect Windows files with ACLs, preserving Docker's required access. Do not represent Unix permission modes as an effective Windows privacy boundary.

## Considered options

- Keep the Linux Compose setup unchanged. Its strength is one configuration model, but it conflates container binding with the Windows LAN identity and relies on Unix ownership assumptions.
- Use a bridge network and require published container ports. Explicit port mapping would simplify the runtime boundary, but requiring publication would reduce the routing contract approved in the specification.
- Run the CLI entirely inside WSL. It would reuse more Unix process behavior, but it would not provide the requested native Windows CLI experience.
- Bind-mount Consul data from NTFS. The files would remain visible beside configuration, but their Linux ownership would need to work through Desktop's filesystem mapping. The volume choice accepts separate state management instead.

## Consequences

Windows has a distinct setup path, an ACL implementation and persistent state outside the Workstation Folder. Normal lifecycle operations preserve that state; destructive reset needs explicit volume handling.

Host networking imposes the Docker prerequisites owned by the specification. It does not establish LAN federation, safe listener exposure or access to unpublished containers. The implementer must supply Windows evidence for each path before acceptance.

The runtime must preserve separate certificate mounts from [ADR-0013](0013-one-yard-ca-and-two-certificates-per-workstation.md). A networking or storage change must not expose Consul's identity key to Traefik.

If the runtime fails the specification's release gates, the maintainer must reconsider this proposal. There is no approved fallback that silently removes routing behavior or Yard participation.
