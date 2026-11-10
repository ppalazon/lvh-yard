---
title: lvh-yard profiles edit keeps the text after a refusal
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-3767a0e4
scope: repo
status: active
replaces: l-e15e6efc
src: agent
touches:
  - cli/internal/command/profiles.go
  - cli/internal/command/profiles_edit_test.go
---

## Decided
- `lvh-yard profiles edit` runs the editor through `sh -c '<editor> "$1"'` (Process.Edit), because $EDITOR can hold arguments such as `code --wait`. Without $EDITOR it runs vi.
- Unchanged means the edited bytes equal the `profiles show` text. Then no PUT is sent.
- A refusal can be bad YAML, another name, a second profile, or a 400 from PUT /{name}. Each one prints the reason and `Edit again? [Y/n]` on stderr. So stdout holds only the result. Enter means yes. `n` or the end of the input means no, and the command fails with "Did not save".
- The temporary file comes from os.CreateTemp, which is owner-only, so basicAuth hashes stay private.

## Watch out
- Tests fake Process.Edit with a stateful function, because $EDITOR is a process matter. It checks the file for the earlier change, which proves that the second round opens the same text.
