# 01: Run native Windows CLI workflows

**What to build:** A Windows 11 x64 user can run the native CLI against an existing Workstation from Windows PowerShell 5.1 or PowerShell 7. Prompts, editor launch, completion, copied commands and log interruption work without Unix tools. Include the minimum platform prefactor needed to make these workflows native; keep one Go CLI.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] The Windows executable builds and runs natively. Version reporting, successful API commands, failed API commands and unknown commands produce the expected consumer-visible results and exit codes.
- [ ] Platform-specific socket ownership and syscall handling no longer prevent Windows compilation. Existing Unix behavior remains intact; no compatibility shim or second CLI remains.
- [ ] Native terminal detection distinguishes interactive and noninteractive use without sh, id, vi or Git Bash. Existing prompt-driven command behavior works through that detection.
- [ ] Editor workflows support native executables and paths with spaces. A native default works when no editor is configured; an invalid editor preserves the edited file and reports failure.
- [ ] Printed commands can be pasted into both supported PowerShell versions. Paths containing spaces, apostrophes and Unicode retain their intended values.
- [ ] PowerShell completion works without removing existing Unix completions.
- [ ] Docker subprocess invocation preserves arguments and the selected working directory. Ctrl+C stops followed Compose logs and returns control to the shell.
- [ ] Native executable checks and existing command-harness tests cover observable successes and failures. Demonstrate shell behavior without Unix tools on PATH; mocks do not substitute for native execution.
- [ ] Update the relevant shell and developer documentation without claiming that full Windows Workstation support has passed its remaining gates.
