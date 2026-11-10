---
title: lvh-yard profiles edit keeps the text until it saves
author: Pablo
date: 2026-11-10
branch: go-cli-review-fixes
id: l-c2a390c2
scope: repo
status: active
replaces: l-3767a0e4
src: agent
touches:
  - cli/internal/command/profiles.go
  - cli/internal/command/profiles_edit_test.go
---

## Decided
- `lvh-yard profiles edit` runs the editor through `sh -c '<editor> "$1"'` (Process.Edit), because $EDITOR can hold arguments such as `code --wait`. Without $EDITOR it runs vi.
- Unchanged means the edited bytes equal the `profiles show` text. Then no PUT is sent and the temporary file goes.
- A refusal can be bad YAML, another name, a second profile, or a 400 from PUT /{name}. Each one prints the reason and `Edit again? [Y/n]` on stderr. Enter means yes. `n` or the end of the input means no.
- The temporary file stays unless the profile is saved or unchanged. The command fails with `Did not save the Route Profile <name>. Your text is in <file>.` on stderr. This holds when the user declines, the input ends, or the editor exits non-zero. The file is the only copy of the user's text, so editProfile deletes it only after a save.
- The temporary file comes from os.CreateTemp, which is owner-only (0600), so basicAuth hashes stay private.

## Watch out
- Tests fake Process.Edit with a stateful function, because $EDITOR is a process matter. It checks the file for the earlier change, which proves that the second round opens the same text.
- A test must remove the kept file itself, because the command leaves it on failure.
