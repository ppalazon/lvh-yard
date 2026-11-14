# 03: Set up a private local Windows Workstation

**What to build:** An ordinary Windows user creates a protected local Workstation, selects its LAN identity safely, starts the generated setup through direct Docker Compose, and reaches a native loopback process through a Local Publication. The long-lived runtime processes remain non-root.

**Blocked by:** 01: Run native Windows CLI workflows.

**Status:** done

- [x] On Windows 11 x64, setup supports Docker Desktop 4.34 or later with Linux containers, the WSL2 backend, host networking enabled and Enhanced Container Isolation disabled. Explain these prerequisites without changing Docker settings automatically.
- [x] Interactive setup creates the Workstation Folder in the application's LocalAppData folder by default. Explicit supported folder overrides work, including spaces, apostrophes and Unicode.
- [x] Require local NTFS storage and give clear guidance for unsupported shared or synced Workstation Folders. A successful write alone is not presented as evidence of supported storage.
- [x] Apply effective Windows ACL protection to private configuration and secrets while preserving required Docker access. A second ordinary local user cannot read protected secrets; testing ACL declarations alone is insufficient.
- [x] Preserve rooted-filesystem containment. Windows-invalid output filenames and escaping reparse points fail with actionable errors rather than writing outside the intended location.
- [x] Suggest a suitable LAN IPv4 address and allow override. Multiple suitable adapters require a choice; ambiguous noninteractive setup requires an explicit value rather than silently choosing a VPN or VM address.
- [x] Separate container binding, Consul advertisement and the default Instance host. Unsupported native interface binding is rejected instead of silently becoming a wildcard listener.
- [x] The generated Windows Compose setup validates and starts directly from the Workstation Folder without hidden launch-time overlays or CLI-only environment preparation.
- [x] Consul and the dashboard run non-root. Consul can write its project-scoped named volume, and the dashboard can read the Docker socket and write a Backup into the protected folder.
- [x] Keep configuration, certificates and exported Backups in the protected Workstation Folder. Runtime containers receive only the certificate roles they need and never the Yard CA private key.
- [x] Default dashboard, proxy, Consul HTTP and Traefik admin listeners are reachable locally and unreachable from a second LAN computer.
- [x] A native Windows process listening only on loopback has passing Health and a successful Reachability result, and a real request through its Local Publication reaches it.
- [x] Preserve template-copy parity, user ownership of configuration and existing Linux/macOS setup defaults. Document the Windows prerequisites, storage and local setup workflow.
- [x] Record real Windows evidence for ordinary-user setup, effective privacy, listener isolation, volume access and loopback routing. If a required runtime path fails, bring the observed scenario to the maintainer before reducing the contract.

Evidence: [ordinary-user Windows Workstation checks](../evidence/03-private-local-windows-workstation.md).
