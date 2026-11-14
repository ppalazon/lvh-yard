# Native Windows CLI workflow evidence

Windows PowerShell 5.1 CLI checks pass for API commands, prompts, explicit
editors, quoting, completion and real Compose log interruption. The native
default editor and every PowerShell 7 check remain open. This evidence does
not establish Windows Workstation support.

## Environment and scope

Observed on 2026-11-14:

- Windows 11 x64, build 26300, Windows PowerShell 5.1.26100.9818.
- Account `test`, an administrator. Ordinary-user checks have not run.
- Docker Desktop 4.99.0, Linux engine 29.9.3, Compose v5.7.0,
  context `desktop-linux`.
- No Go, `sh`, `id`, `vi` or Git Bash. `bash.exe` is the WSL launcher.
- `pwsh.exe` is absent. No PowerShell 7 result is claimed.
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

The final run passed the following checks:

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

The default is configured as `notepad`. It is not yet accepted as a working
native default. The maintainer reported that the editor did not open during
the desktop check. The exact desktop CLI output is still needed to distinguish
an API prerequisite failure from editor launch behavior.

In the initial recovery setup, `fakedash.exe` was started with `Start-Process`
inside SSH. It did not survive that SSH session. A later explicit-editor
probe therefore failed with `The dashboard does not answer` before reaching
an editor. A held-open SSH service was then used to keep the test API on
`http://127.0.0.1:19081` available; a separate SSH session received HTTP 200.

Separate native diagnostics found that System32 Notepad redirects to
Microsoft.WindowsNotepad 11.2609.11.0. Windows Application events recorded
crashes in `Microsoft.UI.Xaml.dll`, exception `0xc000027b`, with WER
`80070005`. These SSH-session events do not establish the cause of the
desktop observation. No registry, app registration or Windows setting was
changed. Windows PowerShell ISE was also probed as an explicit editor; its
process remained alive in SSH session 0, without a window handle. That is
not evidence for a working native default.

The desktop check must use a reachable test API, clear `EDITOR`, change
`X-Team: a` to `X-Team: b`, save, and close the editor. Acceptance requires
that the CLI waits for editing, sends that value to the API, exits 0, and
removes the temporary file. Stand-ins do not satisfy this requirement.

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

- The maintainer must complete the real desktop native-default editor check
  and report any failing behavior. Preserve the text on editor failure.
- The orchestrator must provide PowerShell 7. Repeat version/API/unknown-command
  exits, interactive and noninteractive input, default/spaced/invalid editors,
  printed special-character commands, completion, Docker and log interruption.
- The orchestrator must provide the ordinary-user accounts and Desktop access.
  Repeat native workflows as an ordinary user. A second ordinary user must
  test secret access under the separate privacy acceptance gate.
- Full generated Workstation, persistence, LAN, TLS and release acceptance
  remain outside this CLI evidence.
