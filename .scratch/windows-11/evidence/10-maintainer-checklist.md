# Windows 11 Workstation: maintainer checklist

Nothing below is done. Each item is a decision or an action that belongs to the maintainer. The evidence is in
[10-windows-acceptance.md](10-windows-acceptance.md). Ticket 10 and ticket 02 stay `ready-for-human`.

## 1. Review the evidence

- [ ] Read [10-windows-acceptance.md](10-windows-acceptance.md) and the evidence files `01` to `09` and `10-native-ci.md`.
      Decide whether the observed results meet every release gate of the specification.
- [x] Two results did not pass cleanly in the integrated pass. Both are decided (2026-11-16):
  - A native PowerShell listener on `0.0.0.0` was not reachable from the Linux host. Proven to be the firewall: it
    timed out with no rule, connected with one scoped inbound rule, and timed out again after the rule was removed
    ([10-native-process-firewall.md](10-native-process-firewall.md)). The user guide now says that such a program
    needs an inbound rule that the user adds.
  - `status --strict` fails when Consul TLS is on and a Yard peer has none. The user guide now says so.
- [x] Gaps: you chose no extra runs (2026-11-16). They stay recorded as limits:
  - The second LAN computer is a Linux host. No Windows or macOS peer was used.
  - No real second eligible adapter (VPN, VirtualBox, VMware) was produced. The ambiguity rule is a unit test only.
  - Only Docker Desktop 4.99.0 was tested. The guide says so. It no longer asks for 4.34 or later, because no
    evidence shows that version.
  - The Windows build is `10.0.26300.9974` (display version 26H2, branch `ge_release`). You do not know whether it is
    a public release, so it stays unchecked.
  - PowerShell 7 ran the native checks and a smoke test, not the whole journey.
  - Wildcard-published and explicit-bind container ports, the Docker Desktop restart, image upgrade, destructive
    reset, NTFS live reload and Backup import were observed in the earlier tickets and not repeated.
  - `certmgr.msc`, the import wizard, Edge and Chrome offering a Client Certificate, Firefox's store and SmartScreen
    need a desktop session and were not run. The guide no longer describes them.
  - No macOS Workstation took part in the TLS handoff. The guide names only the Linux test.

## 2. Publish the release

The release and site workflows are `.github/workflows/release.yml` and `.github/workflows/pages.yml`. I pushed and
tagged nothing.

Decided 2026-11-16: release only the PowerShell installer. A double-click `.exe` installer (for example a Go
wrapper that embeds `install.ps1`) and GoReleaser are deferred. No ticket exists for either. The specification
keeps package managers, Authenticode signing and a self-update command out of scope.

- [ ] Push a test tag such as `v0.0.0-rc.1` first, as `docs/development.md` describes. Check that the release holds
      the Windows ZIP with `lvh-yard.exe` and `LICENSE`, and that `SHA256SUMS` lists it.
- [ ] Deploy the site so that `install.ps1` is served. `mise run site:build` copies it to `docs/site/dist` (observed
      locally); no deployment was observed.
- [ ] Run the documented install command on the real Windows 11 computer against the published files. The tested ZIPs
      were built locally by `dev/package-cli.sh`. The release job builds its own. Compare them, or run
      `dev/test-windows-native.ps1` against the published archives.
- [ ] Run the `windows` CI job on GitHub once. It never ran there. Check the artifact transfer, the `cmd` step, the
      runner's shells and line endings after checkout.

## 3. Decide about CI

- [ ] The `windows` job runs as an administrator, because a hosted runner does. It cannot show ordinary-user
      behavior. I could not make an ordinary-user process on an elevated session: a restricted token and
      `Start-Process -Credential` both failed (`10-native-ci.md`). Decide whether to add a non-administrator step,
      for example on a self-hosted runner, or to keep the manual Windows 11 acceptance as the only ordinary-user proof.
- [ ] Decide whether the native job also runs the unit test binaries on every pull request (it does now) or only on
      release tags. It takes about 35 to 50 s on the VM.

## 4. Decide about the firewall policy

- [ ] Docker's backend has inbound Allow rules on the Private and Public profiles on the test VM. They are user-owned,
      and I preserved them. They let wildcard container listeners (Consul's federation ports, and any container
      that binds `0.0.0.0`) reach the LAN and any network that the profile covers. Decide what the user guide tells
      users: keep the broad rules, narrow them to the Yard's addresses, or prefer a rule for each port.
- [x] The guide states that a native Windows process in a Yard Scope Publication needs its own firewall rule (item 1).

## 5. Reconcile the decision records

- [ ] Accept, change or reject ADR-0014 through the repository's decision-recording process. It is `proposed`. I did
      not change its status. It now records that Consul binds `0.0.0.0` on Windows and advertises the LAN address.
      Facts observed since it was written, for you to fold in or leave out:
  - The Instance host is a separate setting, `instance-host` (`YARD_INSTANCE_HOST`), that defaults to the LAN address.
  - The generated `.env` carries `COMPOSE_PROJECT_NAME`, so the Consul volume belongs to one folder.
  - The Windows setup is IPv4-only, as approved on 2026-11-14.
- [ ] The ADR now cites [03-java-ipv4-measurements.md](03-java-ipv4-measurements.md), a file under `.scratch/`.
      `CLAUDE.md` says to delete a feature's folder when its tickets are done. Move that file, or its facts, to a
      durable place before the folder goes.
- [ ] ADR-0010 is unchanged, as the specification requires. Its dependency bullet is now stale: it omits `go-pkcs12`
      (Client Certificate files) and `golang.org/x/sys` (Windows file privacy, imported only by Windows files). Decide
      whether to record that through a new dated entry.

## 6. Documentation after your approval

- [ ] README: add the Windows install line and the platform list. Today `README.md` shows only `install.sh` and no
      Windows text (review item 26). Keep it short, link to the user guide, and state the exclusions and the
      unsigned-binary warning.
- [ ] Landing page: `docs/site/index.html` has the hero terminal (about line 168), "1. Install" (about line 210) and
      "What you need" (about line 544). They show only the Unix installer and no Windows platform line.
- [ ] User guide (review item 22): `docs/user-guide.md` line 146 says "This setup is a draft ... Windows runtime
      acceptance is not complete", and `docs/development.md` line 181 says it too. Remove or move the status text only
      after you approve. I did not touch either, as instructed.
- [ ] User guide wording to review when you approve: the native-process firewall note, the Consul TLS note for mixed
      Yards, and the statement of Docker Desktop versions tested.
- [ ] Review item 24 ("reparse points" in user text) no longer appears in `docs/` or the README (`grep`).
- [ ] `docs/development.md` has the release matrix and the new count of six required Windows-only tests. Check the
      "Releasing" proof steps against the first real release.

## 7. Clean up the test fixtures

- [x] Done on 2026-11-16, with your approval. Removed: the Linux peer project, its volumes and its folder; on the VM, all
      `lvhy*` containers, networks and volumes, and the `lvhy-win0N-dashboard:acceptance` and `lvh-probe:1` images.
      Kept: the public images, the Workstation folders and test binaries under `C:\Users\yarddev\lvh-dev`, the portable
      PowerShell 7, the accounts `yarddev` and `yardother`, the `.wslconfig` files and the VM's CPU topology. The scripts
      and raw output under `~/.local/state/lvh-yard-windows-11/` also stay.

## 8. Items from the review that I did not fix

- [ ] `ws init --address` is a new flag on Linux and macOS too. On Unix it sets `YARD_BIND_ADDRESS` and nothing else
      changes. Keep it for every platform, or hide it on Unix.
- [ ] `ws set instance-host` is accepted on Linux and macOS. On Windows its value is not validated: a loopback or
      wildcard address passes, although other Workstations cannot use it. Decide whether to refuse those values.
- [ ] The handoff probe treats any nonzero exit of `ssh <host> uname -s` (except 255) as a Windows destination. A Unix
      host with a restricted shell would be refused, and the user must copy the files by hand.
- [ ] `mise.toml` copies `install.ps1` into the site build. When this merges to `main`, Pages serves the installer, and
      `release.yml` ships the ZIP on the next tag. Both happen before the README and the landing page change.
- [ ] `.scratch/` evidence is cited by ADR-0014 and `docs/development.md`, while `CLAUDE.md` says to delete a finished
      spec folder. Move what stays to a durable place first.
- [ ] The raw outputs (`evidence/*.out`, the scripts) live outside the repository, under
      `~/.local/state/lvh-yard-windows-11/`. The evidence files cite them by name. Keep them, or drop the names.
- [ ] The Windows listener rule appears in three places: the Go check (`validateNativeListener`),
      `traefik/00-lvh-yard-windows.sh` and the command in `compose.windows.yaml`. A change to one needs the other two.
- [ ] Cross-Workstation reads stalled and a Route flapped on the shared 2-CPU VM (evidence `05` and `06`). The cause was
      never found. The passes of `08` and `10` on the larger VM (4 CPUs, 6 GB, at most two stacks) saw no stall. Decide
      whether that is enough, or whether to repeat a long mixed-Yard run with three or more stacks.
- [ ] A container that publishes a port only on IPv6 gives no Bound Instance on Windows, and no message says why.
      `Registrations.keepBoundInstances` skips it silently. The signal exists: `YARD_IPV4_ONLY` reaches the dashboard as
      `RuntimeNetwork.ipv4Only()`. A visible warning needs a new warning kind (API schema, `openapi.yaml`, CLI and
      page) and a per-binding state that the Service view does not hold. That is not a small change, so I made none.
      Decide whether to add it. Until then the user guide says that IPv6 targets are unsupported on Windows.
