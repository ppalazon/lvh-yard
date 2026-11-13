# Support Windows 11 Workstations

Status: ready-for-agent

## Problem Statement

Windows users cannot install the supported LVH Yard CLI and set up a native Windows Workstation. The published CLI targets and installer cover Linux and macOS. Local setup also assumes Unix commands, socket ownership and file permissions.

Users need more than an executable that compiles for Windows. They need their Workstation to route native processes and containers, protect private keys, preserve state, and join a Yard with Linux and macOS Workstations.

## Solution

Provide a native Windows 11 x64 CLI for Windows PowerShell 5.1 and PowerShell 7. Users install it without administrator rights, then set up and manage a full Workstation through Docker Compose.

Use Docker Desktop with Linux containers and its WSL2 backend. Require Desktop 4.34 or later, host networking enabled, and Enhanced Container Isolation disabled. WSL2 runs Docker's backend; users run the CLI directly in PowerShell.

Preserve native-loopback routing and Container Bindings without published ports. Generate a Windows-appropriate Compose setup that also works with direct Docker Compose commands. Keep Windows files private through ACLs and store Consul state in a persistent named Docker volume.

Windows support is complete only after native CLI checks and real Windows runtime acceptance pass. The runtime requirements below are release gates, not claims of behavior already tested.

## User Stories

1. As a Windows developer, I want to run the CLI natively on Windows 11 x64, so that I can manage my Workstation without a Unix shell.
2. As a Windows developer, I want support for Windows PowerShell 5.1 and PowerShell 7, so that I can use either supported shell.
3. As a Windows developer, I want a per-user installer that needs no administrator rights, so that I can install the CLI without changing machine-wide settings.
4. As a Windows developer, I want the installer available from the public installation site, so that I can follow the documented installation entry point.
5. As a Windows developer, I want a ZIP download containing the executable and license, so that I can install the CLI manually.
6. As a Windows developer, I want downloads checked against SHA256SUMS before installation, so that a damaged or mismatched archive is rejected.
7. As a Windows developer, I want to select a CLI version and installation destination, so that I can control upgrades and local placement.
8. As a Windows developer, I want the installer to add its executable folder to my user PATH without duplicates, so that I can run the CLI from a new terminal.
9. As a Windows developer, I want installation to leave machine PATH and security settings unchanged, so that setup does not silently alter my computer's policy.
10. As a Windows developer, I want to upgrade by rerunning the installer, so that I can use the same installation workflow for new releases.
11. As a Windows developer, I want a failed upgrade to preserve the working executable, so that a failed download or locked destination does not remove my CLI.
12. As a Windows developer, I want clear information about unsigned executables, so that I understand possible Windows security warnings.
13. As a Windows developer, I want clear Docker Desktop prerequisites, so that I can configure the supported runtime before starting a Workstation.
14. As a Windows developer, I want interactive setup to detect my native terminal, so that I receive the intended prompts without Git Bash or Unix tools.
15. As a Windows developer, I want setup to work with spaces, apostrophes and Unicode in paths, so that my account and folder names do not prevent use.
16. As a Windows developer, I want the Workstation Folder under LocalAppData by default, so that configuration and private files stay in local per-user storage.
17. As a Windows developer, I want explicit folder overrides to keep working, so that I can choose another supported local Workstation Folder.
18. As a Windows developer, I want clear guidance for unsupported shared or synced storage, so that I do not mistake a successful file write for supported operation.
19. As a Windows developer, I want private keys and Gossip Keys protected from other ordinary local users, so that my Workstation does not expose its secrets.
20. As a Windows developer, I want protected configuration to remain readable by the containers that need it, so that file privacy does not prevent startup.
21. As a Windows developer, I want each container to receive only its required certificate role, so that Traefik cannot read Consul's identity key or the Yard CA key.
22. As a Windows developer, I want setup to suggest my LAN IPv4 address and allow an override, so that other Workstations can reach mine.
23. As a Windows developer with multiple adapters, I want setup to require a choice when the address is ambiguous, so that it does not silently advertise a VPN or VM-only address.
24. As a developer automating setup, I want ambiguous noninteractive address selection to require an explicit value, so that automation cannot silently pick the wrong interface.
25. As a Windows developer, I want a Compose setup that works directly with Docker Compose, so that I can inspect and manage it without hidden CLI preparation.
26. As a Windows developer, I want Compose lifecycle commands to preserve paths and arguments, so that Docker operates on my selected Workstation Folder.
27. As a Windows developer, I want Ctrl+C to stop followed logs, so that I can return to my shell.
28. As a Windows developer, I want a working native editor workflow, so that I can edit Route Profiles without Unix editors or shells.
29. As a Windows developer, I want printed commands to be valid PowerShell, so that I can copy them even when paths contain special characters.
30. As a Windows developer, I want PowerShell completion, so that I can discover and enter CLI commands more easily.
31. As a Windows developer, I want ordinary API commands to work against my Workstation, so that I can manage Services and their setup from the CLI.
32. As a Windows developer, I want a Local Publication to reach a native process listening only on loopback, so that I do not need to expose that process to the LAN.
33. As a Windows developer, I want Container Bindings to reach containers without published ports, so that I do not need to change those containers' port exposure.
34. As a Windows developer, I want Container Bindings to work across unrelated Docker networks, so that my development projects do not need a shared Compose network.
35. As a Windows developer, I want wildcard-published container ports to use the right address for Local and Yard Publications, so that both routing scopes work.
36. As a Windows developer, I want explicitly bound published ports handled correctly, so that their selected addresses remain meaningful to routing and checks.
37. As a Yard member, I want a Windows Workstation to join Linux and macOS Workstations, so that operating system choice does not split the Yard.
38. As a Yard member, I want Services published by Windows reachable from other Workstations, so that Windows can host shared development tools.
39. As a Windows developer, I want to reach Remote Services through my Workstation, so that I can use tools hosted elsewhere in the Yard.
40. As a Windows developer, I want the dashboard and proxy listeners local by default, so that setup does not expose them to other LAN computers.
41. As a Windows developer, I want unsupported interface-binding choices rejected clearly, so that a local listener does not silently become a wildcard listener.
42. As a Yard member, I want documented federation firewall requirements, so that I can allow the needed traffic without the installer changing firewall policy.
43. As a Windows developer, I want Consul checks, Reachability tests and Routes to agree on Instance addresses, so that passing checks correspond to working requests.
44. As a Windows developer, I want Consul and the dashboard to run non-root with working socket and storage access, so that runtime integration does not require privileged long-lived processes.
45. As a Windows developer, I want Consul state to survive normal lifecycle operations and Docker Desktop restart, so that routine maintenance does not lose my setup.
46. As a Windows developer, I want a separate destructive reset with a data-loss warning, so that deleting state requires an explicit action.
47. As a Windows developer, I want to know that deleting the Workstation Folder leaves Docker-managed state, so that I can remove all state intentionally.
48. As a Windows developer, I want to export and import Backups, so that I can transfer the setup covered by a Backup without copying raw Consul data.
49. As a Windows developer, I want the dashboard to write Backups into the protected Workstation Folder, so that privacy and Backup use work together.
50. As a Windows developer, I want local Yard CA and certificate commands to work, so that I can configure Workstation TLS and Client Certificates.
51. As a Windows developer, I want HTTPS and Consul mutual TLS to work, so that secure routing and federation remain available.
52. As a Windows developer, I want client certificate export and manual browser-import guidance, so that I can use Routes requiring a Client Certificate.
53. As a Yard member, I want documented manual certificate handoff for Windows endpoints, so that I can set up another Workstation without exposing the Yard CA key.
54. As a Windows developer, I want unsupported automated certificate transfers rejected before remote changes, so that Unix-shell assumptions cannot leave a partial Windows setup.
55. As a Windows developer, I want routing and TLS file changes observed by Traefik, so that editing supported configuration takes effect on NTFS storage.
56. As a Windows developer, I want invalid filenames and file-containment escapes rejected clearly, so that file operations cannot write outside their intended location.
57. As a Windows developer, I want image upgrades to preserve my Compose and environment edits, so that updating LVH Yard does not overwrite my networking or storage choices.
58. As a developer moving between operating systems, I want fresh setup plus Backup import documented, so that I do not rely on portable raw Consul state or Compose files.
59. As an existing Linux or macOS user, I want my defaults and Unix-to-Unix certificate transfer preserved, so that Windows support does not disrupt my Workstation.
60. As a release maintainer, I want native Windows checks and real Workstation evidence before publication, so that support claims match observed behavior.

## Implementation Decisions

- Extend the single Go CLI rather than introducing a separate Windows CLI. Keep the dashboard in Java and preserve existing command names and option contracts.
- Keep platform-specific terminal detection, identity, editor launch, file protection and command rendering behind the existing process operations where appropriate. Do not add interfaces solely for tests.
- Isolate Unix socket ownership and syscall usage so that the CLI and relevant tests compile natively on Windows. Do not require sh, id, vi or Git Bash.
- Support both approved PowerShell versions for installation and documented command rendering. Add PowerShell completion without removing existing Unix completions.
- Pass Docker arguments directly, preserving argument boundaries and the selected working directory. Printed instructions must use PowerShell-compatible escaping on Windows.
- Launch a native editor, with a native default when no editor is configured. Support executable paths with spaces and preserve the edited file when editor invocation fails.
- Reject Windows-invalid output filenames and containment escapes with actionable errors. Preserve the existing rooted-filesystem protection and verify reparse-point handling.
- Extend release packaging with a Windows x64 ZIP containing the executable and license. Include the ZIP in archive upload, release publication and checksum generation.
- Publish a PowerShell installer through the existing installation site and site build. Preserve the existing installer's version and destination overrides.
- Default executable installation to a separate bin folder under the application's LocalAppData folder. Change user PATH only, without duplicates, and explain when a new terminal is needed.
- Verify the selected download before replacement. Leave the installed executable intact after download, checksum or replacement failure, including a Windows-locked destination.
- Keep distribution checksum-verified and unsigned. Explain that checksum verification does not provide Authenticode publisher authentication.
- Default the Windows Workstation Folder to the application's LocalAppData folder. Preserve explicit overrides and existing Unix defaults. Keep executable files separate from configuration and runtime files.
- Require local NTFS storage. Apply Windows ACLs to private Workstation files and secret exports while preserving Docker's required access. Administrative access is outside the ordinary-user privacy guarantee.
- Keep configuration, certificates and exported Backups in the Workstation Folder. Preserve certificate-role separation and never mount the Yard CA private key into runtime containers.
- Store Windows Consul state in a named Docker volume scoped to the Compose project. Initialize its Linux ownership separately while keeping long-lived Consul and dashboard processes non-root.
- Preserve the volume during normal stop, start, recreate and upgrade operations. Document a separate destructive reset and make clear that folder deletion alone leaves Docker-managed state.
- Generate a Windows-appropriate, user-owned Compose setup. Direct Docker Compose commands must work without a hidden wrapper, launch-time overlay or CLI-only environment preparation.
- Preserve template-copy parity checks and existing Linux/macOS setup behavior. Image upgrades must not silently overwrite user networking, storage or environment edits.
- Keep the runtime on Docker Desktop host networking. Separate container binding, Consul advertisement and the default Instance host instead of assuming one address represents all three.
- Suggest a Windows LAN IPv4 address and allow override. Require a choice when suitable adapters remain ambiguous; require an explicit value for ambiguous noninteractive setup.
- Advertise addresses reachable from other Workstations. Do not register VM-only addresses or a host-relative Docker hostname as substitutes for a Yard-visible Instance address.
- Keep dashboard and proxy listeners local by default. Keep Consul's HTTP API and Traefik's admin listener local. Reject unsupported interface binding instead of silently widening exposure.
- Document required federation firewall access. Neither the installer nor normal setup changes firewall rules, Docker settings or PowerShell execution policy.
- Preserve native-loopback routing and Container Bindings without published ports, including containers on unrelated networks. Verify published ports with wildcard and explicit address bindings separately.
- Keep Consul checks, dashboard Reachability and Traefik routing consistent with the registered endpoint. A passing check cannot substitute for a successful request through the Route.
- Preserve the existing meaning and contents of a Backup. Cross-platform moves use fresh setup and Backup import rather than automatic Compose migration or raw Consul-state transfer.
- Support local CA, certificate import/issue, TLS enable/disable and client certificate export. Browser trust and client certificate import remain manual.
- Use documented manual certificate handoff for Windows senders and recipients. Reject unsupported native Windows automated transfers early; Unix senders must detect unsupported Windows destinations before Unix preparation commands.
- Preserve existing Unix-to-Unix automated certificate transfer. Never transfer the Yard CA private key during Workstation certificate handoff.
- Update user and development documentation with the implementation. Update short installation entry points without duplicating the full guide. Advertise Windows support only after acceptance passes.
- Do not introduce a new dashboard API or change glossary meanings merely for platform detection. Any internal settings needed for the separated network addresses must preserve the existing Service and Instance contracts.

## Testing Decisions

- Test consumer-visible behavior at the highest practical interfaces. A good test catches an incorrect command result, unsafe access, lost state or failed request. Avoid private-helper assertions, source-text checks and exact wording assertions.
- Use the approved Windows process seam for the CLI and installer. Observe executable results, prompts, generated files, effective ACLs, PATH changes, editor behavior and Docker subprocess behavior.
- Use the approved running-Workstation seam for runtime acceptance. Start the generated setup with direct Docker Compose and observe real Service URLs, Health, Reachability, persistence and a second LAN Workstation.
- Reuse the existing command harness for deterministic failure cases and the built-executable test pattern for native execution. Keep fakes confined to controlled CLI behavior; they cannot prove Windows ACL or Docker behavior.
- Reuse the existing Reachability integration-test pattern, which sends requests through real Traefik. Its simulated LAN forwarding is prior art, not evidence of Windows federation.
- Test the CLI command module, platform process operations, installation/distribution modules and generated setup through these interfaces. Test dashboard registration/routing integration through a running Workstation rather than adding new production seams.

### Native Windows and installer acceptance

- Run the executable and installer natively under both supported PowerShell versions. Verify ordinary-user installation, version reporting, reinstall and upgrade.
- Verify version and destination overrides, checksum rejection, user PATH deduplication and preservation of machine PATH.
- Prove that download, checksum and replacement failures preserve the working executable. Include a destination locked by Windows.
- Run interactive setup without Unix tools on PATH. Exercise spaces, apostrophes and Unicode in installation paths, Workstation Folders, copied commands and editor invocation.
- Prove PowerShell completion works and Ctrl+C stops followed Compose logs. An invalid editor command must report failure without losing the edited file.
- Verify default and overridden folder selection, invalid filename rejection, and file-containment protection. Include Windows reparse points.
- Verify secret protection from a second ordinary local user. Checking ACL configuration alone is insufficient; the other user must fail to read protected secrets.
- Verify unsupported native Windows automated certificate transfer fails before transfer. Verify a Unix sender identifies an unsupported Windows destination before remote preparation.

### Real Windows Workstation acceptance

- Use Windows 11 x64, the approved Desktop configuration and an ordinary user account. Pair it with at least one Linux or macOS Workstation on the same LAN.
- Validate and start generated configuration with direct Docker Compose from the Workstation Folder. No earlier CLI invocation may be required to repair its environment.
- Verify Consul and the dashboard run non-root, the dashboard can read the Docker socket, Consul can write its volume, and the dashboard can export a Backup into the protected folder.
- Create persistent setup, then stop/start, recreate, upgrade images and restart Docker Desktop. Verify that the setup remains available after each operation.
- Verify destructive reset targets only the selected project's Consul state. Verify its documentation names the data loss and the state left behind by folder deletion.
- Reach default local listeners from Windows and prove they are unreachable from another LAN computer. Check the dashboard, proxy, Consul HTTP and Traefik admin interfaces.
- Route a Local Publication to a native Windows process listening only on loopback. Verify the Instance check, Reachability result and actual Route response.
- Bind containers without published ports on unrelated Docker networks. Verify their checks and actual Route responses without changing port exposure.
- Test wildcard-published and explicitly bound container ports. Verify registered addresses and successful requests for the applicable Local and Yard Publications.
- Join the Yard in both directions. Request a Windows-hosted published Service from the other platform, then request a Remote Service through the Windows Workstation.
- Exercise multiple adapters and an explicit LAN override. Verify advertisement and registered Instance addresses do not silently become VPN or VM-only addresses.
- Verify CA creation, certificate issue/import, TLS enable/disable, HTTPS, Consul mutual TLS and client certificate export.
- Change routing and TLS files on NTFS bind mounts. Observe the changed behavior through real Traefik requests.
- Export and import a Backup using the documented cross-platform setup workflow. Verify its defined contents without copying raw Consul state.

### Regression checks and release evidence

- Preserve existing Linux/macOS defaults, user-owned Compose edits, template parity and Unix-to-Unix certificate transfer. Reuse the existing command, permission, security and integration tests for these contracts.
- Native Windows CI may use Windows Server for executable coverage, but it cannot replace Windows 11 runtime acceptance. Avoid administrator-only test conditions or preinstalled Unix tools concealing prerequisites.
- Run the repository's unit checks and the narrow integration checks affected by implementation. Run the required repository checks before committing implementation.
- Record the environment, command or scenario, and observed result for each real Windows acceptance item. The maintainer reviews this evidence before the release claims Windows support.
- Runtime failures block acceptance. Do not weaken tests, require published ports or remove Yard participation to obtain a passing result.

## Out of Scope

- Windows ARM64, Windows Server as a supported user environment, and CMD-specific user experience.
- A WSL-only CLI experience, applications running inside user WSL distributions, Hyper-V backend support and Windows containers.
- Docker Desktop without the approved host-network configuration or with Enhanced Container Isolation enabled.
- Network shares and cloud-synced Workstation Folders.
- Package-manager distribution, Authenticode signing and a new CLI self-update command.
- Installing Docker, changing Docker settings, opening firewall ports or relaxing PowerShell execution policy automatically.
- Automated Windows certificate handoff and Windows SSH-server support.
- A full port of the repository's developer toolchain or a maintained automated Windows 11 Docker acceptance runner.
- Automatic migration of existing Compose files between operating systems, portable raw Consul data and expanding the contents of a Backup.
- Changes to the domain model or Linux/macOS defaults unrelated to Windows support.

## Further Notes

The user approved the support contract in the design interview and confirmed the testing seams for this specification. This specification is the single local tracker record for the feature; implementation has not started here.

The [proposed Windows runtime ADR](../../docs/adr/0014-windows-workstations-use-docker-desktop.md) records the networking and storage trade-offs. Keep it proposed until the runtime gates pass. Existing accepted ADRs remain unchanged.

Use the [domain glossary](../../GLOSSARY.md) for Workstation, Publication, Container Binding, Backup and related terms. A Backup does not become a raw Consul-volume backup or an archive of private certificates.

The implementation must respect the [single Go CLI decision](../../docs/adr/0010-the-cli-is-written-in-go.md), [owned-files decision](../../docs/adr/0011-consul-files-traefik-flags-and-owned-files.md), [host-network decision](../../docs/adr/0012-services-keep-host-networking.md), and [certificate-role decision](../../docs/adr/0013-one-yard-ca-and-two-certificates-per-workstation.md).

Source inspection found Unix-specific initialization, process operations and file protection in the current CLI. The [existing command harness](../../cli/internal/commandtest/harness.go), [built-executable test](../../cli/cmd/lvh-yard/main_integration_test.go), and [Reachability integration tests](../../dashboard/src/test/java/cc/lvhy/yard/dashboard/ReachabilityIT.java) provide the testing prior art.

[Docker's host-network documentation](https://docs.docker.com/engine/network/drivers/host/#docker-desktop), read on 2026-11-13, supports bidirectional host-loopback communication but excludes binding native interface addresses from containers. It does not establish this application's LAN federation or listener isolation.

The implementer owns the remaining runtime evidence: LAN federation, local-only listeners, unpublished-container access, non-root socket permissions, volume initialization and NTFS file watching. These are implementation acceptance obligations, not unanswered product questions.

If a runtime requirement cannot be met, the implementer must bring the failed scenario to the maintainer before changing the approved contract. The maintainer owns any scope change and the final support claim.
