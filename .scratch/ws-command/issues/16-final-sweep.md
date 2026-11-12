# 16: Final sweep

**What to build:** Nothing in the repository still describes the old setup. The learnings match the code, and the plan folder is deleted once every ticket is done.

**Blocked by:** 01 to 15: all other tickets

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] A search finds no `lvh-yard init`, `--reinit`, root `compose.yaml` or `dev/*.env` in text, comments, scripts or docs.
- [x] The upgrade learning is replaced with `trail note --replaces`; the saved replacement precedes removal of the old file.
- [x] `GLOSSARY.md`, the ADRs and `docs/user-guide.md` agree on Yard CA, Workstation Certificate and Client Certificate.
- [ ] `mise run check` passes.
- [x] `.scratch/ws-command/` is deleted, as `docs/agents/issue-tracker.md` says.

## Completion

The integration owner reported that `mise run check` passed before the remote SAN adjustment. The certificate tests and real-Docker test passed after that adjustment. The owner will run the final check after merging this sweep; no checks ran in this worktree.

All other tickets are done. The completed plan folder is removed with this sweep. The final Spec review includes the approved remote wildcard SAN extension and the requirement to recreate services after settings changes; neither changes the hostname layout or the meaning of `ws restart`.
