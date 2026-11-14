# 01: Run native Windows CLI workflows

**What to build:** A Windows 11 x64 user can run the native CLI against an existing Workstation from Windows PowerShell 5.1 or PowerShell 7. Prompts, editor launch, completion, copied commands and log interruption work without Unix tools. Include the minimum platform prefactor needed to make these workflows native; keep one Go CLI.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [x] The Windows executable builds and runs natively. Version reporting, successful API commands, failed API commands and unknown commands produce the expected consumer-visible results and exit codes.
- [x] Platform-specific socket ownership and syscall handling no longer prevent Windows compilation. Existing Unix behavior remains intact; no compatibility shim or second CLI remains.
- [x] Native terminal detection distinguishes interactive and noninteractive use without sh, id, vi or Git Bash. Existing prompt-driven command behavior works through that detection.
- [x] Editor workflows support native executables and paths with spaces. A native default works when no editor is configured; an invalid editor preserves the edited file and reports failure.
- [x] Printed commands can be pasted into both supported PowerShell versions. Paths containing spaces, apostrophes and Unicode retain their intended values.
- [x] PowerShell completion works without removing existing Unix completions.
- [x] Docker subprocess invocation preserves arguments and the selected working directory. Ctrl+C stops followed Compose logs and returns control to the shell.
- [x] Native executable checks and existing command-harness tests cover observable successes and failures. Demonstrate shell behavior without Unix tools on PATH; mocks do not substitute for native execution.
- [x] Update the relevant shell and developer documentation without claiming that full Windows Workstation support has passed its remaining gates.

## Comments

2026-11-14: [Observed evidence](../evidence/01-native-windows-cli-workflows.md)
records Windows PowerShell 5.1 and PowerShell 7.7.0 results. The ticket is
not done. All listed behaviors passed as administrator `test`; scoped
ordinary-user executable and shell checks also passed.

- Both shells passed API exit codes, explicit editors, failure retention,
  printed special-character commands and completion.
- New normal desktop windows in both shells opened real default Notepad,
  waited for edit/save/close, saved the changed Route Profile and exited 0.
- PowerShell 7 ConPTY prompts and real Docker Compose with Unicode,
  apostrophes and spaces passed. Ctrl+C returned from followed logs.
- Ordinary user `yarddev` passed all eight native smoke tests under Windows
  PowerShell 5.1, plus PowerShell 7 printed-command and completion subtests.
  Those editor and Docker checks used native recording executables.
- The orchestrator must arrange `yarddev` desktop and Docker Desktop access.
  Ordinary-user real default editor, interactive prompts, real Docker and
  followed-log interruption remain open. Privacy is a separate gate.
- The complete native command-unit binary still has POSIX mode, copied-fixture
  and Unix SCP expectation failures. The evidence records these; no native
  full-suite pass is claimed.
