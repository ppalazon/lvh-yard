# Evidence: early review fixes, batch A

Environment: Windows 11 VM, ordinary account `yarddev`, second ordinary account `yardother`,
Windows PowerShell 5.1.26100 and PowerShell 7.7.0. Binaries were cross-built on Linux with
`GOOS=windows GOARCH=amd64 CGO_ENABLED=0`. Observed on 2026-11-14.

## Item 7: effective access of the private files

- `TestWindowsProtectionDeniesAnotherUserByEffectiveAccess` (unit, `yarddev`, PS 5.1 host):
  passed. It runs `ws init` and `ws tls ca create`, then opens every entry of the folder with a
  token whose user and Administrators are deny only. A control file that grants Users read is
  readable with that token. The owner opens every entry.
- The same test against an executable whose DACL also granted `BU` read failed with
  "another user opened ...: <nil>" for every entry. The DACL then went back to the original.
- `TestBuiltExecutableProtectsTheWorkstationFolderFromAnotherUser` (integration, `yarddev`
  owner, real interactive logon of `yardother`): passed. Against the weakened executable it
  failed with "yardother opened ... <nil>" for every entry.

## Item 10: installer and the user PATH

- Old installer, PS 5.1, seeded `%USERPROFILE%\bin` as REG_EXPAND_SZ: the acceptance script
  failed. The installer had written `C:\Users\yarddev\bin`.
- New installer, `dev\test-install-windows.ps1` as `yarddev`: all steps passed in PS 5.1 and in
  PS 7.7.0. The checks cover REG_EXPAND_SZ and REG_SZ kinds, byte-exact text after install and
  reinstall, a `%VAR%` entry that names the install folder, and a missing PATH value.
- `reg query HKCU\Environment /v Path` before and after each run was identical
  (`REG_SZ`, with its empty entry `;;`).

## Item 8: unit tests on the Windows runner

- First native run of the whole `cli/internal/command` unit suite: 63 failures, all from
  Unix assumptions (mode bits, symlinks, no `--address`, Unix templates). After the test fixes:
  155 PASS, 0 FAIL, 7 SKIP (symlink tests; junction test covers the rule).
- `dev\test-windows-native.ps1` on the VM as `yarddev`, PS 5.1, with a package built like the
  CI job: integration tests passed (including the account test with `yardother`), the unit test
  binaries passed (234 passed, 7 skipped), the four required Windows-only tests ran, and the
  installer acceptance passed. Exit code 0.
- Not run: the same script in PowerShell 7 (the VM was reserved for another agent after the
  first run), and the GitHub workflow itself. `actionlint` accepted `ci.yml`.

## Item 15: LAN address on Windows

- `TestWindowsLANAddressIsOnAnAdapterWithAGateway`: passed on the VM. `LANAddress` returned
  `192.168.122.117`, the only address of an adapter with a gateway.
- The rule that rejects a candidate off the default route is a pure function with unit tests
  (`TestChooseLANAddress`). The VM has no VirtualBox, VMware or VPN adapter, so that case was
  not seen on a real adapter.
