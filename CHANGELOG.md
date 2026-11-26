# Changelog

This file lists the changes that people see in each release of LVH Yard, newest first. It follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [Semantic Versioning](https://semver.org/). Before 1.0.0, any minor release can change how LVH Yard works, and a breaking change comes first in its release, with the action to take.

Put a change that people see in `[Unreleased]` when it lands. Before a release, rename `[Unreleased]` to the new version and date. The release workflow publishes that section as the release notes.

## [Unreleased]

## [0.2.0] - 2026-11-26

This entry compares 0.2.0 with 0.1.0. The pre-release 0.2.0-rc.1 of 2026-11-19 has no entry of its own.

### Breaking changes

- **`lvh-yard init` is now `lvh-yard ws init`, and `init --upgrade` and `init --reinit` are gone.** After you install a new `lvh-yard`, run `lvh-yard update`, then `lvh-yard ws compose update` and `lvh-yard ws compose recreate` in each Workstation Folder. `ws compose update` saves a Backup first. See "Upgrading" in the [user guide](https://github.com/ppalazon/lvh-yard/blob/v0.2.0/docs/user-guide.md#upgrading).
- **A Yard is formed with an invitation, and `--peers` and `--gossip-key` are gone.** On the Workstation that starts the Yard, run `lvh-yard ws tls ca create`, then `lvh-yard ws yard init`. For each new member, run `lvh-yard ws yard share <name>` there and `lvh-yard ws yard join <invitation>` on the new Workstation. The invitation is an AES-256 encrypted zip file, and every connection of a Yard uses Consul TLS.
- **A new Workstation on Linux and macOS listens on `127.0.0.1` only, until `ws yard init` or `ws yard join` opens it.** Before, Consul listened on the LAN from the first start. A Workstation that must be reachable by the Yard needs the commands above. Windows keeps its LAN address, because Docker Desktop needs it.
- **`ws init` asks for the Base Domain and has no default.** 0.1.0 offered `lvh.me`. Without a terminal, give `--base-domain`, for example `--base-domain lvh.me`.
- **A new Workstation serves the Traefik admin page on port 7781, not 8080.** The hostname `traefik-admin.<Base Domain>` does not change. A Workstation that exists keeps the value in its `.env`.
- **`ws init` no longer writes `COMPOSE_PROJECT_NAME`.** Compose names the project after the Workstation Folder, so each Workstation Folder needs its own name. Two folders with the same name share one `consul-data` volume.

### Added

- **Windows 11 (x64) is supported.** `lvh-yard.exe` runs natively and Workstations run on Docker Desktop with the WSL2 backend and host networking. A Windows Workstation can join a Yard with Linux and macOS Workstations. Install it with `Invoke-RestMethod https://lvhy.cc/install.ps1 | Invoke-Expression` in Windows PowerShell 5.1 or PowerShell 7, or from the ZIP archive of the release. The limits are in "Known issues".
- **Install and update from the command line.** `https://lvhy.cc/install.sh` and `install.ps1` check the archive against `SHA256SUMS`. `lvh-yard update` installs the latest release or a given one, and `lvh-yard update --check` shows the release and changes nothing. `update` replaces only an executable from a release archive. A package manager or a build from the sources keeps its own update path.
- **The `lvh-yard ws` command group runs a Workstation.** It has `init`, `show` and `set`; `compose` with `start`, `stop`, `restart`, `recreate`, `down`, `logs` and `update`; `yard` with `init`, `share`, `join`, `standalone` and `peers`; and `tls`. `ws compose update` also runs migrations that a release adds for an existing Workstation Folder.
- **HTTPS in three independent parts.** `lvh-yard ws tls ca create` makes a Workstation CA for testing and development. `ws tls https` serves HTTPS from that CA or from your own certificate files. `ws tls client` checks Client Certificates, and a Route Profile with `clientCertificate` requires one. `ws tls mtls` lets Traefik call a Service over mutual TLS, and `lvh-yard publish <service> --mutual-tls on` turns it on for a Service. A Route that requires a Client Certificate has no HTTP Route.
- **Reachability tests check Routes over HTTPS** when HTTPS is on, and the dashboard shows the TLS state on the Status page.
- **Route Diagram.** A page of the dashboard draws the path of a request to a Service, from the browser to its Instances.
- **Containers page.** It follows each port of a container, filters by state and by whether a Service follows the container, and edits a Container Binding on the Service page. A Container Binding prefers the host port that suits the Scope of its Publication.
- **A new Service warning, `unencrypted-instance`.** It appears when HTTPS is on and Traefik calls an Instance off loopback over plain HTTP.
- **A version badge on the Status page** links to the releases on GitHub. The dashboard makes no call to the internet to show it.
- **A guide and a landing page at `https://lvhy.cc/`.** The guide at `https://lvhy.cc/docs/` has the tutorial, recipes, concepts and references. The dashboard header has a Docs button.
- **CLI.** `lvh-yard service create` lists a command for every kind of Instance. `lvh-yard register static` asks for the port and host at a terminal. `lvh-yard catalog` shows the Scope and filters by it. `lvh-yard status` prints the dashboard address.

### Changed

- **Instance Priority counts only the Instance Source in use** on the Service page, the Catalog page and in `lvh-yard catalog`, which shows the count as N/M. The Service state still counts every Instance.
- **Linux and macOS keep Consul data in the `consul-data` Docker volume**, and `ws compose down -v` removes it. A folder from an older `lvh-yard` keeps its `./consul-data` folder, and the first `ws compose update` prints a note about it. See "Upgrading" in the user guide to move to the volume.
- **CLI text is shorter and uses one style.** A command that you run stands alone on its own line. Machine-read output, such as Backups, JSON and the `ws show` lines, does not change.
- **The dashboard follows the colors of the `lvhy` theme.** Tables are compact, Service warnings are alerts with a topic, and a hostname links to HTTP and to HTTPS.
- **Backups are written in format version 3.** The dashboard reads versions 2 and 3. A Backup keeps the Client Certificate profiles and the mutual TLS setting of a Publication.

### Fixed

- **A Workstation that Consul cannot read shows as a warning, not as alive.**
- **Aliases count in the Service state, the counts and Reachability.** Before, the Instances of an Alias did not count.
- **`ws init` leaves an existing Workstation Folder alone when it refuses to run.**

### Security

- **A new Workstation closes its Consul ports to the LAN** until you open it with `ws yard init` or `ws yard join`. See the breaking changes.
- **Every Yard connection is encrypted.** The Gossip Key encrypts the gossip, and Consul TLS checks each Workstation by its certificate.
- **Files that LVH Yard writes are private to the user.** The CLI keeps managed files inside the Workstation Folder, and Consul runs as the user of that folder. On Windows, the folder is private to its owner.

### Known issues

- **`ws init` suggests `localhost` as a Base Domain, but its HTTPS certificate does not work.** `curl` and Chromium reject a wildcard with one label. Use a Base Domain of two labels, such as `dev.localhost`. Tracked in [#16](https://github.com/ppalazon/lvh-yard/issues/16).
- **The Windows Workstation supports IPv4 only.** The executable is not signed, so Windows may warn you.
- **A native Windows program that listens on `0.0.0.0` needs an inbound firewall rule** that you add, before other computers reach it.
- **`lvh-yard status --strict` fails when Consul TLS is on and a Peer of the Yard has none.** This affects a Yard that mixes Workstations with and without Consul TLS.
- **Windows was tested by hand on one Windows 11 computer with Docker Desktop 4.99.0.** The CI of the project runs the CLI tests and the installer on Windows Server. Other Docker Desktop versions are not tested.

### Not included

- **No signed Windows executable, no package-manager packages and no double-click Windows installer.** The PowerShell installer and the archives are the supported ways to install.
- **No Podman support.** Docker with Compose is the only container runtime.

[Unreleased]: https://github.com/ppalazon/lvh-yard/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/ppalazon/lvh-yard/releases/tag/v0.2.0
