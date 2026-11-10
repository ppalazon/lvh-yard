# 15: `completions` and the smoke test

Status: done

Blocked by: 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 14

Spec: `../spec.md`.

## What to build

A shell user gets Tab completion for every command, option and value, and the built executable is proven to work as a process.

- A hidden `completions <shell>` command that prints a Bash or a Zsh script. Cobra's own `completion` command is turned off. Values such as the states of `catalog --state` and the shells of `completions` complete.
- Every command and option of the Java CLI exists in the Go CLI. A test lists them and fails when one is missing.
- One smoke test runs the built executable against a hand-written fake dashboard. It shows that `main`, the version and the exit code work. By ADR-0008 it is an integration test, because it uses another process.

## Acceptance criteria

- [ ] `source <(lvh-yard completions bash)` and `source <(lvh-yard completions zsh)` work, as the user guide says.
- [ ] The test of command and option names passes.
- [ ] The smoke test passes under `mise run check`.
