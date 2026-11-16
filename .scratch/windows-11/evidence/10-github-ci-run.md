# First run of the Windows CI jobs on GitHub

Date: 2026-11-16. Pull request #3 (draft) from `windows-11` to `main`. The `windows-package` and `windows` jobs had
never run on GitHub before.

## Runs

| Run         | Commit    | Result  | Cause                                                                                                           |
| ----------- | --------- | ------- | --------------------------------------------------------------------------------------------------------------- |
| 43855640917 | `2688bf7` | failure | Both `windows` jobs: `TestPrintedCommandsKeepTheirValuesInTheShell`. The rest passed.                           |
| 43856590298 | `94b53eb` | failure | Both `windows` jobs: the `templates` unit tests could not open `config\.env.example` and other dotfiles.        |
| 43857640970 | `002fe51` | success | All six jobs passed: `check`, `format`, `secrets`, `windows-package`, `windows (powershell)`, `windows (pwsh)`. |

## Two defects the runner found

1. **A virtual machine's network card was refused as a tunnel adapter.** The runner's default-route adapter is
   `Ethernet 3`. Noninteractive `ws init` stopped with "the default route is on "Ethernet 3", which looks like a VPN or
   virtual adapter". My word list for tunnel adapters held `hyper-v`, `vmware` and `virtualbox`, so a Windows guest on
   those hypervisors would have been refused too. The list now has tunnel words only
   (`vpn`, `tap`, `tun`, `wireguard`, `wintun`, `ppp`, `tailscale`, `zerotier`) plus the point-to-point flag. A test with
   the names of real guest cards (Hyper-V, VMware, VirtualBox, VirtIO) failed before the change and passes after it.
   The ordinary-user VM (a KVM guest with a VirtIO card) never hit this, which is why earlier runs missed it.
2. **The artifact dropped dotfiles.** `actions/upload-artifact@v4` skips hidden files unless `include-hidden-files: true`
   is set. The `windows-package` job packed `config/.env.example`, `config/.env.windows.example` and their committed
   copies, and the upload dropped them. The step now sets the input. The same tests had passed on the Windows 11
   computer, where the files were copied by hand.

## Observed in the successful run

- Job times in the passing run: `windows (powershell)` 45 s, `windows (pwsh)` 52 s, `windows-package` 31 s, `check`
  251 s, `format` 45 s, `secrets` 14 s.
- Runner image `windows-2025-vs2026`, Windows PowerShell 5.1.26100.33648 and PowerShell 7, account `runneradmin`.
  The script printed "ADMINISTRATOR: this run does not show ordinary-user behavior" in both jobs.
- Each `windows` job ran `dev/test-windows-native.ps1` with `-AllowAdministrator`. Each log has 639 `--- PASS` lines,
  0 `--- FAIL` lines and 9 `--- SKIP` lines (subtests count as lines), and ends with `PASS installer acceptance`.
- The `windows-package` job packaged two versions, built the integration and unit test binaries for Windows, and passed
  them through the artifact to the Windows jobs.

## Not shown

- The jobs run as an administrator, so they say nothing about ordinary-user behavior. The manual Windows 11 acceptance
  covers that.
- A job on a Windows Server image is not Windows 11 with Docker Desktop.
- The `Release` job's Windows step ran in the test release (`10-test-release.md`), not in this run.
