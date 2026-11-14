# 01: Run native Windows CLI workflows

**What to build:** A Windows 11 x64 user can run the native CLI against an existing Workstation from Windows PowerShell 5.1 or PowerShell 7. Prompts, editor launch, completion, copied commands and log interruption work without Unix tools. Include the minimum platform prefactor needed to make these workflows native; keep one Go CLI.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [x] The Windows executable builds and runs natively. Version reporting, successful API commands, failed API commands and unknown commands produce the expected consumer-visible results and exit codes.
- [x] Platform-specific socket ownership and syscall handling no longer prevent Windows compilation. Existing Unix behavior remains intact; no compatibility shim or second CLI remains.
- [x] Native terminal detection distinguishes interactive and noninteractive use without sh, id, vi or Git Bash. Existing prompt-driven command behavior works through that detection.
- [ ] Editor workflows support native executables and paths with spaces. A native default works when no editor is configured; an invalid editor preserves the edited file and reports failure.
- [ ] Printed commands can be pasted into both supported PowerShell versions. Paths containing spaces, apostrophes and Unicode retain their intended values.
- [ ] PowerShell completion works without removing existing Unix completions.
- [x] Docker subprocess invocation preserves arguments and the selected working directory. Ctrl+C stops followed Compose logs and returns control to the shell.
- [x] Native executable checks and existing command-harness tests cover observable successes and failures. Demonstrate shell behavior without Unix tools on PATH; mocks do not substitute for native execution.
- [x] Update the relevant shell and developer documentation without claiming that full Windows Workstation support has passed its remaining gates.

## Comments

2026-11-14: [Observed evidence](../evidence/01-native-windows-cli-workflows.md)
records Windows PowerShell 5.1 results and the remaining gates. The ticket is
not done. Checked criteria name behavior proved in that environment; they do
not claim PowerShell 7 or ordinary-user acceptance.

- The maintainer must complete the native-default editor check on the desktop.
  Explicit native editor paths and retained text after failure passed, but
  the default editor has not passed. The desktop report says no editor opened;
  the exact CLI output and a reachable test API are needed for diagnosis.
- The orchestrator must provide PowerShell 7. Repeat version and API exit
  codes, unknown commands, interactive and noninteractive input, default,
  spaced and invalid editors, printed commands with special characters,
  completion, Docker invocation and Ctrl+C on followed logs.
- Printed commands and completion passed in Windows PowerShell 5.1.
  Their checkboxes remain open for PowerShell 7.
- The orchestrator must arrange the ordinary-user accounts and Desktop access.
  Ordinary-user native workflow and privacy acceptance remain open.
- The complete native command-unit binary still has POSIX mode, copied-fixture
  and Unix SCP expectation failures. The evidence records these; no native
  full-suite pass is claimed.
