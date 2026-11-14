# Native Windows CI evidence

The new CI jobs have not run on GitHub. No workflow run, runner image or
GitHub Actions behavior was observed. This file records what ran on the
Windows 11 test VM: the same script that the `windows` job runs, with the same
packaged archives and the same Windows test binary. It does not show that the
workflow passes on a hosted runner. Windows Server CI cannot replace the
Windows 11 acceptance of ticket 10.

## What was checked without GitHub

- `actionlint` 1.7.12 reported nothing for `.github/workflows/ci.yml`.
- The commands of the `windows-package` job ran on Linux with Go 1.27.3 and
  produced `9.8.7/` and `9.8.8/` (four archives and a `SHA256SUMS` file
  each) and `lvh-yard-integration.test.exe`. The folder was copied to the VM.
- The `windows` job's script `dev/test-windows-native.ps1` ran on the VM in
  each shell. The VM has no Go, Git or Unix tool.

## Environment

Observed on 2026-11-14:

- Windows 11 x64, `10.0.26300.9974`.
- Windows PowerShell `5.1.26100.9818` and portable PowerShell `7.7.0`. The
  portable PowerShell is not on the machine PATH, so each run added its folder
  to the PATH of the process, as the runner image has `pwsh.exe` on its PATH.
- Account `yarddev`: its token has no Administrators group. Account `test`:
  its SSH session has the Administrators group enabled.
- Command line of each run (`$R` is `C:\Users\<account>\lvh-dev\10`):

```text
powershell.exe | pwsh.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass
  -File $R\repo\dev\test-windows-native.ps1 -Package $R\package
  -Installer $R\repo\docs\site\install.ps1 -PowerShellMajor 5 | 7
  [-AllowAdministrator]
```

`-ExecutionPolicy Bypass` is process-scoped, as the VM helper starts shells.
The script printed the policy of every scope. Only `Process` was `Bypass`.
The acceptance script compared all scopes and the machine PATH before and
after, and they matched.

## Results, ordinary user `yarddev`

Both runs exited with code 0 (`&& echo exit=0`). They are the runs of the
final script.

| Shell                             | Observed result                                                                                                                                            |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Windows PowerShell 5.1.26100.9818 | 25 `--- PASS` lines of the integration tests, no FAIL or SKIP. `PASS installer acceptance`, with every scenario of `dev/test-install-windows.ps1` passing. |
| PowerShell 7.7.0                  | The same: 25 `--- PASS` lines, no FAIL or SKIP, and `PASS installer acceptance`.                                                                           |

In each run:

- The script resolved `powershell.exe` and `pwsh.exe`. The integration tests
  passed in both shells, including `TestPowerShellCompletesCommandsAndValues`
  and `TestPrintedCommandsKeepTheirValuesInTheShell`. The script checks each
  of those subtest names, so a shell that the tests skip fails the run.
- `lvh-yard.exe` was extracted from `lvh-yard-9.8.7-windows-amd64.zip`, and
  reported `lvh-yard 9.8.7`. The tests ran against it through
  `YARD_TEST_EXECUTABLE`; the test binary built nothing and no Go ran on the
  VM.
- The installer scenarios used the packaged 9.8.7 and 9.8.8 ZIP files and
  their manifests: default folder and latest release, manual ZIP checksum,
  spaces, apostrophe and Unicode in the folder, reinstall, PATH
  deduplication, upgrade, manifest and archive download failure, checksum
  mismatch, locked file, directory collision, a new shell, and unchanged
  machine PATH and execution policy.
- Before the checks, a folder `fakeunix` with `sh.exe`, `id.exe` and `vi.exe`
  (copies of `whoami.exe`) was on the PATH. The script printed
  `Dropped from PATH` for it, and the Unix-tool check passed afterwards. The
  script also dropped `C:\Users\yarddev\AppData\Local\Microsoft\WindowsApps`,
  which holds the WSL launcher alias `bash.exe`.

The first two ordinary-user runs, made before the final edits to the script,
also passed in both shells. Their exit status was not read, because the
command expanded `%ERRORLEVEL%` too early. Their output ended with
`PASS installer acceptance`.

## Results, administrator `test`

The script is meant to run as an administrator on the runner. These runs used
`-AllowAdministrator` and the same `fakeunix` check. Each exited with code 0.

| Shell                             | Observed result                                                                                                                                   |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Windows PowerShell 5.1.26100.9818 | 25 `--- PASS`, no FAIL or SKIP, `PASS installer acceptance`. The header line said `ADMINISTRATOR: this run does not show ordinary-user behavior`. |
| PowerShell 7.7.0                  | The same.                                                                                                                                         |

Without `-AllowAdministrator`, the same elevated account failed at once with
`This account is an administrator. Run as an ordinary user, or pass
-AllowAdministrator ...`, before any test ran (observed in Windows PowerShell
5.1).

## Why the job runs as an administrator

A hosted runner account is an administrator. `dev/test-install-windows.ps1`
requires an ordinary user, so the job needs a way to run without that
condition. I tried two ways to give the script an ordinary-user process on an
elevated session, and both failed on the VM:

- A restricted token (the Administrators group denied, no privileges), with
  `CreateProcessAsUser`. The new PowerShell started, but every program it
  started failed with `Access is denied`, because the process inherited the
  console of the elevated parent. With a console of its own
  (`CREATE_NO_WINDOW`), the process failed with `0xC0000142`. With no console
  (`DETACHED_PROCESS`), the script printed nothing and `whoami.exe` failed.
- `Start-Process -Credential` as `yarddev`, from the elevated session of
  `test`. It ended with exit code `-1073741502` (`0xC0000142`).

Both were observed in an SSH session, which differs from a hosted runner. They
were not tried on a runner. I removed that code. The job therefore does not
show ordinary-user behavior, and says so in its output and in
`docs/development.md`. The ordinary-user results above are from `yarddev`.

## Not verified

- That the workflow runs on GitHub: the checkout, the artifact upload and
  download, the `cmd` step, the runner's `powershell` and `pwsh`, and the
  `windows-latest` image.
- The PATH folders that the runner image has. The script removes folders that
  contain a Unix tool and fails when one still resolves, but the VM has none of
  Git's folders. The `fakeunix` folder only shows the mechanism.
- Behavior of the scripts after Git's checkout converts line endings to CRLF.
  The VM copy of the scripts has LF endings.
- The hosted runner's Windows Server version and its Windows Defender
  behavior with the packaged executable.
