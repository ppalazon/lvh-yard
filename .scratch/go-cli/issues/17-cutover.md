# 17: Cutover

Status: ready-for-agent

Blocked by: 15, 16

Spec: `../spec.md`.

## What to build

The Go CLI takes the place of the Java CLI, and the repo has one CLI and no GraalVM code.

- Delete the Java CLI, its module in the root Maven project, the `native*` and `install` tasks of `mise.toml`, and the `cli-native` job of the Release workflow. Add an `install` task that builds the Go executable and copies it to `~/.local/bin`.
- Rename the Go folder to the CLI's place, and fix the paths in `mise.toml`, the hooks and the workflow.
- Let the `release` job need the Go job in place of `cli-native`.
- Update `docs/development.md` (build, tests and release sections, and the size table with the new numbers), `docs/user-guide.md` where it describes install, and `CLAUDE.md` where it names the test tasks.
- Replace the stale learnings with `trail note --replaces`: the native executable, `-O3`, the native build, `NativeRecords`, and the CLI learnings that name Java classes. Run `git rm` on an old file only after trail saves the new one.
- Delete `.scratch/go-cli/`.

## Acceptance criteria

- [ ] `mise run check` passes from a clean checkout.
- [ ] A run of the Release workflow on a test tag makes the three archives and `SHA256SUMS`, and the `release` job finishes.
- [ ] No file outside the ADRs and the git history names GraalVM, `native-image`, `NativeRecords` or picocli.
- [ ] `mise run audit` finds no done spec in `.scratch/` and no learning that names a deleted file.
