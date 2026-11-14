# Native Windows CLI workflow evidence

Windows PowerShell 5.1 and PowerShell 7 CLI checks pass for API commands,
prompts, editors, quoting, completion and real Compose log interruption as
administrator `test`. Both shells also passed the scoped ordinary-user checks
below. Ordinary-user desktop editor and real Docker checks remain open.
This evidence does not establish Windows Workstation support.

## Environment and scope

Observed on 2026-11-14:

- Windows 11 x64, build 26300, Windows PowerShell 5.1.26100.9818.
- Account `test`, an administrator. Ordinary-user results have their own scope below.
- Docker Desktop 4.99.0, Linux engine 29.9.3, Compose v5.7.0,
  context `desktop-linux`.
- No Go, `sh`, `id`, `vi` or Git Bash. `bash.exe` is the WSL launcher.
- PowerShell 7 was absent during the initial Windows PowerShell 5.1 checks.
- Windows files: `C:\Users\test\lvh-dev\01\`.

The approved seams are the command harness and the built executable.
`cli/cmd/lvh-yard/main_integration_test.go` runs the executable against a
controlled HTTP server. `native_integration_test.go` uses native recording
executables for editor and Docker argument checks. These prove CLI behavior,
not a real dashboard, Docker Desktop or a GUI editor.

The interrupted run's observed results were recovered from the Implement01
session. Its final native integration run printed `PASS` and `exit code 0`.
The recovery preserved all partial source changes and confirmed that
`windows-11` was an ancestor before proceeding.

## Build and automated executable checks

The executable and test binary were cross-compiled on Linux, copied to the
Windows folder, and run there without installing Go:

```sh
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags="-s -w -X main.version=9.8.7" -o /tmp/lvh01/lvh-yard.exe ./cmd/lvh-yard
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go test -c -tags integration -o /tmp/lvh01/smoke.test.exe ./cmd/lvh-yard
```

The native command was:

```powershell
cd C:\Users\test\lvh-dev\01
$env:YARD_TEST_EXECUTABLE = 'C:\Users\test\lvh-dev\01\lvh-yard.exe'
.\smoke.test.exe '-test.count=1' '-test.v'
$LASTEXITCODE
```

The Windows PowerShell 5.1 run passed the following checks:

| Test                                                  | Observed result                                                                                                                                                                                                                                                                  |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `TestBuiltExecutableAgainstAFakeDashboard`            | Version stdout was `lvh-yard 9.8.7` and exit 0. Healthy API status exited 0. Failing status, an unknown command and an unreachable API each exited 1. Unknown and unreachable commands wrote errors to stderr.                                                                   |
| `TestBuiltExecutableSavesWhatTheEditorWrote`          | A native recording editor changed `X-Team: a` to `X-Team: b`. The CLI saved through the API, exited 0 and removed its temporary file. Quoted executable paths with spaces and `--wait`, names on PATH with `--wait`, and whole unquoted executable paths with spaces all passed. |
| `TestBuiltExecutableKeepsTheTextWhenTheEditorFails`   | A missing editor and an editor that changed the text then exited 3 both caused CLI exit 1. The API received no save. The file retained the original or changed text, and stderr named that file.                                                                                 |
| `TestBuiltExecutableGivesDockerItsArgumentsAndFolder` | The native recording `docker.exe` received separate arguments `compose`, `--project-directory`, the selected folder, `logs`, `-f`, `consul`. Its working directory was that folder. Its exit 7 propagated to the CLI.                                                            |
| `TestBuiltExecutableReportsMissingDocker`             | With only an empty folder on PATH, the CLI exited 1 and named Docker in stderr.                                                                                                                                                                                                  |
| `TestBuiltExecutableAsksNothingWithoutATerminal`      | Init with redirected input exited 1, named the missing `--workstation` flag and created no folder.                                                                                                                                                                               |
| `TestPrintedCommandsKeepTheirValuesInTheShell`        | Both printed start commands ran through Windows PowerShell 5.1. The recording Docker executable received the intended folder `Yard d'été ’24 日本`, preserving spaces, straight and typographic apostrophes, and Unicode.                                                        |
| `TestPowerShellCompletesCommandsAndValues`            | The generated script loaded with `Out-String \| Invoke-Expression`. `TabExpansion2` returned `compose`, all six Compose actions, `local` and `yard`, and `powershell`.                                                                                                           |

PowerShell command text reached the parser through `-EncodedCommand`, using
UTF-16. The test executes the exact printed lines. It does not claim a
physical clipboard paste or physical Tab key press.

## Console and noninteractive input

The interrupted run used `win-tty powershell.exe -NoLogo -NoProfile`, which
allocates a Windows ConPTY console. It ran:

```powershell
.\lvh-yard.exe ws --dir 'C:\Users\test\lvh-dev\01\tty-init' init
```

The CLI asked for the Workstation name, rejected `WS Bad`, asked again,
accepted `ws-tty`, then asked for the Base Domain. Accepting its default
wrote the files and produced exit 0. The socket-group warning remained;
this was a prompt check, not acceptance of generated Windows setup.

The recovery also used direct noninteractive SSH:

```sh
win-ssh 'C:\Users\test\lvh-dev\01\lvh-yard.exe ws --dir C:\Users\test\lvh-dev\01\noninteractive init'
```

It printed `Missing --workstation: no terminal to ask for it` and exited 1.
A subsequent `Test-Path` check found no `noninteractive` folder.

## Real Docker and Ctrl+C

The real Compose probe used this file in `Yard d'été ’24 日本`:

```yaml
name: lvh01
services:
  whoami:
    image: traefik/whoami:latest
```

From another working directory, Windows PowerShell ran:

```powershell
$root = 'C:\Users\test\lvh-dev\01'
$folder = (Get-ChildItem $root -Directory | Select-Object -First 1).FullName
Set-Location $root
& "$root\lvh-yard.exe" ws --dir $folder compose start
```

The CLI launched real Docker Compose. It created `lvh01_default` and
`lvh01-whoami-1`, then exited 0. `docker inspect` reported the exact selected
folder in `com.docker.compose.project.working_dir` and its `compose.yaml` in
`com.docker.compose.project.config_files`.

A real ConPTY PowerShell session then ran:

```powershell
.\lvh-yard.exe ws --dir $folder compose logs -f
```

Compose printed `whoami-1 | ... Starting up on port 80`. Sending Ctrl+C
returned the PowerShell prompt. A following `Write-Output 'SHELL_RETURNED'`
printed `SHELL_RETURNED`, and another prompt appeared. No followed-log CLI
or Docker process remained in the subsequent process query.

The probe driver's first attempt waited for the wrong log text and timed
out. The corrected attempt observed the interruption and shell return, then
its strict output assertion timed out because ConPTY inserted an ANSI cursor
sequence before `SHELL_RETURNED`. These are driver failures; the shell output
above is the observed result. No interrupt exit code is claimed.

A request for `compose logs whoami` exited 1 because the CLI accepts only its
Workstation service names. The successful followed-log check selected no
service. Cleanup used `lvh-yard ... compose stop` and direct
`docker compose --project-directory $folder down`; both exited 0 and removed
the probe's container and network. Other agents' resources were untouched.

## Default editor investigation

The real native default passed in a new normal Windows PowerShell 5.1 window
on the VM desktop. The maintainer reserved the console for this check.
Keyboard input and screenshots used libvirt's console APIs. No existing
console or unrelated app received typed commands.

With the test API kept alive at `http://127.0.0.1:19081`, the desktop ran:

```powershell
cd C:\Users\test\lvh-dev\01
Remove-Item Env:EDITOR -ErrorAction SilentlyContinue
$env:TEMP = $PWD.Path
$env:TMP = $PWD.Path
.\lvh-yard.exe --dashboard http://127.0.0.1:19081 profiles edit team
"CLI_EXIT=$LASTEXITCODE"
Get-Content .\saved.json
```

Real Notepad opened `route-profile-528513835.yaml`, showing `X-Team: a`.
The CLI stayed alive while the document was open. Native process inspection
showed the CLI and its Notepad descendants in desktop session 1.
Keyboard input changed only the owned document to `X-Team: b` and saved it.
Closing that Notepad window returned to the CLI, which printed:

```text
Saved the Route Profile team.
CLI_EXIT=0
{"headers":{"customRequestHeaders":{"X-Team":"b"}}}
```

A subsequent `Test-Path` returned false for that exact temporary file.
No desktop CLI or Notepad process remained. The test closed only its new
PowerShell window and its own input-driver dialogs. Temporary driver scripts
and diagnostic files were removed; the test API service was stopped.

Screenshots captured the open document, saved text and final shell output
under `/tmp/lvh01/default-open.png`, `default-saved.png` and
`default-result.png`. Native default acceptance uses this actual GUI result,
not the recording editor.

The earlier maintainer report said no editor opened. Its CLI output and timing
were no longer available, so its cause could not be established. Recovery
found that the initial test API, launched with `Start-Process` inside SSH,
did not survive that session. An explicit-editor probe then failed before
editor launch with `The dashboard does not answer`. The successful desktop
check used a held-open SSH service and a separately observed HTTP 200.

SSH-only diagnostics also recorded packaged Notepad crashes in
`Microsoft.UI.Xaml.dll`, exception `0xc000027b`, with WER `80070005`.
They do not establish the cause of the earlier desktop report. An explicit
Windows PowerShell ISE probe stayed alive without a session-0 window handle;
it was not adopted as the default. No production editor change, registry
change, app registration change or Windows setting change was needed.

## PowerShell 7 acceptance

On 2026-11-14, the portable PowerShell executable at
`C:\Users\test\lvh-dev\orchestrator\pwsh7\pwsh.exe` reported version `7.7.0`,
edition `Core`, on Windows build `26300`. The checks used account `test`.
Only the child session PATH gained the portable folder. No machine or user
PATH, execution policy or editor setting changed.

The helper selected that full path through `WIN_PS`. The existing Windows
test binary and version `9.8.7` CLI ran with these commands:

```powershell
$env:PATH = 'C:\Users\test\lvh-dev\orchestrator\pwsh7;' + $env:PATH
Set-Location 'C:\Users\test\lvh-dev\01'
$env:YARD_TEST_EXECUTABLE = "$PWD\lvh-yard.exe"
.\smoke.test.exe '-test.count=1' '-test.v' '-test.run=^TestBuiltExecutable'
.\smoke.test.exe '-test.count=1' '-test.v' '-test.run=^(TestPrintedCommandsKeepTheirValuesInTheShell|TestPowerShellCompletesCommandsAndValues)$/^pwsh[.]exe_'
```

Both runs printed `PASS` and exited 0. The first ran all six
`TestBuiltExecutable` tests, including all editor cases. Version and healthy
API calls exited 0; failed API calls, unknown commands and an unreachable API
exited 1. Spaced editor paths and editor arguments saved the changed text.
Missing and failed editors retained their files and sent no API save.
Docker argument, working-directory, missing-Docker and noninteractive prompt
checks also passed.

The shell filter selected only PowerShell 7 subtests. All four completion
cases and both printed start commands passed. The exact printed lines ran
through PowerShell 7, preserving the `Yard d'été ’24 日本` folder.
An initial filter ended with `^pwsh.exe$`, selected no subtests and printed
`no tests to run`. It is not counted as shell acceptance.

### Real console and Docker

A new ConPTY session ran the full portable PowerShell path with
`-NoLogo -NoProfile`. Init in the fresh `ps7-tty-init` folder rejected
`WS Bad`, accepted `ws-ps7`, asked for the Base Domain and accepted its default.
The CLI exited 0. The socket-group warning remained, so this proves prompts,
not generated Windows Workstation setup. The probe removed its init folder.

PowerShell 7 ran the real Compose commands from the earlier Docker section,
using the existing special-character folder from another working directory.
Start exited 0. Docker inspection returned the exact folder and
`compose.yaml` path in the two Compose labels. A separate PowerShell 7
ConPTY session ran `compose logs -f` and observed `Starting up on port 80`.
Ctrl+C returned the shell prompt. The command
`Write-Output ('SHELL_' + 'RETURNED_PS7')` printed `SHELL_RETURNED_PS7`,
and another prompt appeared. No followed-log process remained in the
subsequent process query. No interrupt exit code is claimed.

Cleanup ran CLI `compose stop` and direct Compose `down`. Both exited 0
and removed only the probe container and network.

### Real desktop default editor

The orchestrator reserved the console. The probe opened a new normal
PowerShell 7 window and confirmed version `7.7.0`. A held-open SSH service
kept the controlled API alive on `http://127.0.0.1:19082`; an HTTP request
returned 200 before editor launch. The shell ran the earlier desktop commands
with this port and saved to `saved.ps7.json`.

With `EDITOR` unset, real Notepad opened
`route-profile-1695848107.yaml` and showed `X-Team: a`.
Native process inspection showed the waiting CLI and Notepad descendants
in desktop session 1. Keyboard input changed only that document to
`X-Team: b` and saved it. Closing the owned Notepad window returned to the CLI:

```text
Saved the Route Profile team.
CLI_EXIT=0
{"headers":{"customRequestHeaders":{"X-Team":"b"}}}
```

`Test-Path` returned false for that exact temporary file, and the process
query found no remaining CLI or Notepad process. The probe closed its new
shell and stopped its API service. It left unrelated apps untouched and
released the console reservation.

Screenshots under `/tmp/lvh01/` record the selected original document
(`ps7-notepad-selected.png`), saved text (`ps7-default-saved.png`) and final
shell result (`ps7-cli-result.png`). No production editor workaround was used.

## Scoped ordinary-user checks

On 2026-11-14, the orchestrator created `yarddev` and `yardother`.
SSH reported `t-x\yarddev` and `t-x\yardother`; both accounts returned false
for `WindowsPrincipal.IsInRole(Administrator)`. The orchestrator supplied
the following native results from its completed `bg_49` and `bg_52` runs.

Under `yarddev`, the existing CLI and smoke binary ran from
`C:\Users\yarddev\lvh-dev\01`, with `YARD_TEST_EXECUTABLE` naming that CLI.
The complete smoke invocation from the build section passed all eight tests
in Windows PowerShell 5.1. This covers version/API/unknown-command results,
recording editors and failure retention, recording Docker arguments and
working directory, missing Docker, noninteractive init, printed commands
and completion. PowerShell 7 was not on that session's PATH and was skipped.

The orchestrator then extracted the official portable PowerShell archive in
that account's own test folder. It reported `7.7.0`. With only the child
session PATH changed, this invocation passed all four PowerShell 7 completion
subtests and both PowerShell 7 printed-command subtests, exiting 0:

```powershell
.\smoke.test.exe '-test.count=1' '-test.v' '-test.run=TestPowerShellCompletesCommandsAndValues/pwsh.exe|TestPrintedCommandsKeepTheirValuesInTheShell/pwsh.exe'
```

These ordinary-user runs used recording editor and Docker executables.
They do not prove ordinary-user desktop Notepad, real Docker Desktop,
interactive prompts or followed-log interruption. Docker Desktop remains
installed only for `test`; the orchestrator must arrange the ordinary
account's desktop and Docker access before those checks.

## Host verification

On Linux, the recovery ran these focused checks successfully:

```sh
go test ./internal/command ./internal/client
go test -tags integration -count=1 ./cmd/lvh-yard
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build ./...
GOOS=windows GOARCH=amd64 go vet ./...
GOOS=windows GOARCH=amd64 go vet -tags integration ./cmd/lvh-yard
GOOS=darwin GOARCH=arm64 CGO_ENABLED=0 go build ./...
```

`mise run check` passed, including dashboard verification, Go vet, the gated
Go linters (`0 issues`), Go unit tests and built-executable integration tests.
This does not establish native macOS execution.

The interrupted run's complete native command-unit binary failed. Its
captured output is `/tmp/lvh01/baseline-command.txt`: POSIX mode assertions,
a missing copied `config/.env.example` fixture, and Unix SCP path expectations
failed. That suite has not been made green or represented as green. ACL and
certificate-transfer acceptance belong to their own Windows work.

## Remaining acceptance

- With the ordinary account's desktop and Docker access ready, repeat real
  default-editor editing, interactive prompts, real Docker invocation and
  followed-log Ctrl+C. The scoped ordinary-user checks above already passed.
- Secret access by a second ordinary account remains a separate privacy gate;
  no privacy result is claimed here.
- Full generated Workstation, persistence, LAN, TLS and release acceptance
  remain outside this CLI evidence.
