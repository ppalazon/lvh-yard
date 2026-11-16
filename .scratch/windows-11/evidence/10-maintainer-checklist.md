# Windows 11 Workstation: maintainer checklist

Nothing below is done. Each item is a decision or an action that belongs to the maintainer. The evidence is in
[10-windows-acceptance.md](10-windows-acceptance.md). Ticket 10 and ticket 02 stay `ready-for-human`.

## 1. Review the evidence

- [x] The maintainer read [10-windows-acceptance.md](10-windows-acceptance.md) and approved its results on 2026-11-16.
      Tickets 03, 05 and 06 became `done` with their open limits recorded as accepted limits. Their unobserved boxes
      stay unticked. Ticket 02 and ticket 10 stay `ready-for-human` until the release and the documentation are done.
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

The release and site workflows are `.github/workflows/release.yml` and `.github/workflows/pages.yml`. On 2026-11-16 I
pushed the test tag `v0.1.1-rc.1` with your approval and nothing else. The result is in [10-test-release.md](10-test-release.md).

Decided 2026-11-16: release only the PowerShell installer. A double-click `.exe` installer (for example a Go
wrapper that embeds `install.ps1`) and GoReleaser are deferred. No ticket exists for either. The specification
keeps package managers, Authenticode signing and a self-update command out of scope.

- [x] Test tag `v0.1.1-rc.1` (your choice, 2026-11-16): the release job passed. The release is a pre-release with the
      Windows ZIP (`lvh-yard.exe` and `LICENSE`), the Linux and macOS archives and `SHA256SUMS`. All four checksums
      verify. `releases/latest` is still `v0.1.0`. I first added `--prerelease` for hyphenated tags, because the
      installers use `releases/latest`.
- [ ] Delete the test release, the tag and the image `lvh-yard-dashboard:0.1.1-rc.1` when you have finished looking at
      them. I did not, because the token has no `delete:packages` scope, and you may want to look first.
      `gh release delete v0.1.1-rc.1 --cleanup-tag` removes the release and the tag. Delete the image version in the
      package settings on GitHub.
- [ ] Deploy the site so that `install.ps1` is served. `mise run site:build` copies it to `docs/site/dist` (observed
      locally); no deployment was observed.
- [x] Install on the real Windows 11 computer against the published files, partly. As `yarddev`, in Windows PowerShell 5.1
      and PowerShell 7, the installer file from the branch installed the published ZIP: the executable's SHA-256
      matched the one inside the ZIP, `--version` printed `lvh-yard 0.1.1-rc.1`, a reinstall left one PATH entry, and
      the PATH was restored exactly. Not run: the documented one-line command (the site is not deployed).
- [x] Run the `windows` CI job on GitHub once: done on pull request #3 on 2026-11-16 ([10-github-ci-run.md](10-github-ci-run.md)).
      The first two runs failed and found two real defects, fixed on the branch: a virtual machine's own network card
      was refused as a tunnel adapter, and the artifact dropped hidden files. The third run passed all six jobs.

## 3. Decide about CI

- [x] Decided 2026-11-16: keep the `windows` job as an administrator job. The manual Windows 11 acceptance stays the
      only ordinary-user proof, and the limit stays documented. No self-hosted runner, no non-administrator step.
- [x] Decided 2026-11-16: the native job keeps running the unit test binaries on every pull request. On the hosted
      runner the two `windows` jobs took 45 and 52 s in the passing run, and `windows-package` took 31 s. Move it to
      release tags only if that changes.

## 4. Decide about the firewall policy

- [x] Decided 2026-11-16: document the facts and change nothing. Docker's backend has inbound Allow rules on the Private
      and Public profiles on the test VM (user-owned, preserved). The user guide now says that such a rule lets any
      computer on those networks reach every `0.0.0.0` container port, Consul's included, and that a narrow rule of the
      user's own does not limit that. It lists three steps: a Private network only where trusted, Consul TLS for a Yard
      that is not fully trusted, and no `0.0.0.0` container port unless wanted. The guide does not show how to narrow
      Docker's rule. Not checked: who created these rules, and whether every Docker Desktop install has them.
- [x] The guide states that a native Windows process in a Yard Scope Publication needs its own firewall rule (item 1).

## 5. Reconcile the decision records

- [x] Decided 2026-11-16: the maintainer accepted ADR-0014. I folded the facts observed since it was written into the
      ADR first: the separate `instance-host` setting, `COMPOSE_PROJECT_NAME` in the generated `.env`, the new
      `golang.org/x/sys` dependency and the Docker firewall rule fact. The IPv4-only section was already there. The
      ADR now has a section "Acceptance, 2026-11-16" that lists the gates still open. An accepted ADR can change only
      its status line, so any later change needs a new ADR.
- [x] The ADR no longer cites a file under `.scratch/`. I moved the Java socket measurements into the ADR as a table, and
      it names the specification in plain text instead of linking it.
- [x] ADR-0010 stays unchanged, as the specification requires. `golang.org/x/sys` is recorded in ADR-0014. ADR-0010's
      list also omits `go-pkcs12`, but `main` already had that dependency before this work, so that gap is older than
      the Windows port. Leave it, or record it through a new ADR if you ever want that list fixed.

## 6. Documentation after your approval

On hold (decided 2026-11-16): do this section after the test tag and the first `windows` CI run on GitHub, so the
wording matches what is published.

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

Decided 2026-11-16, on my recommendations, except where noted.

- [x] `ws init --address` is a new flag on every platform. Kept. On Unix it only sets `YARD_BIND_ADDRESS`.
- [x] `ws set instance-host`: Windows now refuses `0.0.0.0`, multicast addresses and the broadcast address, which no
      connection can reach. I did **not** refuse loopback, as I first recommended. The user guide says that a process
      on `127.0.0.1` works for Local Scope, a Service with Yard Scope already shows a warning for a loopback Instance,
      and routing to native loopback processes is a core feature. A pure function (`checkInstanceHost`) holds the
      rule, with a test table that runs everywhere. A Windows test checks that `ws set` refuses the values, leaves
      `.env` unchanged, and accepts `127.0.0.1`. Unix still accepts any IPv4 address.
- [x] The handoff probe keeps treating a nonzero `ssh <host> uname -s` exit (except 255) as a Windows destination. A
      false refusal is the safe failure, and the message names the manual steps.
- [x] The installer went live when the pull request merged. Closed. What is left is the missing release: `v0.1.0` and
      the test release are deleted, so `releases/latest` returns 404 until a new release exists.
- [ ] `docs/development.md` line 294 still cites `.scratch/windows-11/evidence/`, while `CLAUDE.md` says to delete a
      finished spec folder. Do it when the folder is deleted. Tickets 02 and 10 are still `ready-for-human`.
- [x] The raw outputs and scripts stay in `~/.local/state/lvh-yard-windows-11/`. The evidence files cite them by name.
- [x] The Windows listener rule stays in three places (Go, `traefik-windows.sh`, the Compose command). Documented.
- [x] The cross-Workstation stalls on the 2-CPU VM stay an accepted limit. They did not recur on the larger VM.
- [ ] A container that publishes a port only on IPv6 gives no Bound Instance on Windows, and no message says why.
      `Registrations.keepBoundInstances` skips it silently. The signal exists: `YARD_IPV4_ONLY` reaches the dashboard as
      `RuntimeNetwork.ipv4Only()`. A visible warning needs a new warning kind (API schema, `openapi.yaml`, CLI and
      page) and a per-binding state that the Service view does not hold. That is a feature and not a fix. Open: your
      decision. My recommendation is to wait until a user hits it. The user guide says that IPv6 targets are
      unsupported on Windows.
