---
title: lvh-yard profiles edit keeps the text after a refusal
author: Pablo
date: 2026-11-05
branch: worktree-agent-a2ddeed53a3da556a
cost: { minutes: 9, tokens: 10572 }
id: l-e15e6efc
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Profiles.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
---

## Decided
- Profiles.Edit runs $EDITOR through `sh -c '$EDITOR "$1"'`, because $EDITOR can hold arguments such as `code --wait`. Without $EDITOR it runs vi.
- Unchanged means the edited text equals the `profiles show` text, byte for byte. Then no PUT is sent.
- A refusal can be bad YAML, another name, a second profile, or a 400 from PUT /{name}. Each one prints the reason and `Edit again? [Y/n]` on stderr. So stdout holds only the result. Enter means yes. `n` or the end of the input means no.
- On no, the command keeps the temporary file and prints its path, so the change is not lost. It exits with 1.
- The temporary file comes from Files.createTempFile, which is owner-only on POSIX, so basicAuth hashes stay private.
- A non-zero editor exit (for example vi `:cq`) saves nothing.

## Watch out
- Tests use YardTest.yardWithEditor: a real process, because $EDITOR is an environment variable. A stateful editor script (it checks the file for the earlier change) proves that the second round opens the same text.
- trail check flags YardTest for "test name service" and "yard end workstation". They are word matches on stub JSON. Ignore them.
