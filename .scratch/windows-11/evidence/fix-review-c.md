# Review fixes C: native Windows run

Environment (2026-11-15): Windows 11 VM, ordinary account `yarddev`, Windows PowerShell 5.1.26100.9818, the packaged ZIPs
and test binaries built from branch `win11/fix-review-c` with the commands of the CI `windows-package` job. Only the Windows
ZIP of each version went into the package folder. `YARD_TEST_OTHER_USER=yardother` was set.

`dev/test-windows-native.ps1 ... -PowerShellMajor 5` exited 0 after 44 s. Machine PATH and execution policy were unchanged.
There was no `--- FAIL` line. The output ended with `PASS packaged executable: integration tests, including both
PowerShells`, `PASS unit tests: 259 passed, 7 skipped` and `PASS installer acceptance`. The new tunnel and
Unix-wording tests are unit tests of the shared package (they run in the unit binary on Windows as well).

The reset check for ticket 09 is in `09-preserve-transfer-workstation-setup.md`. Its raw output and this run's output are in
`~/.local/state/lvh-yard-windows-11/evidence/fixc-*.out`.
