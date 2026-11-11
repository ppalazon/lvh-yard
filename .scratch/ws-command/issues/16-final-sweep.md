# 16: Final sweep

**What to build:** Nothing in the repository still describes the old setup. The learnings match the code, and the plan folder is deleted once every ticket is done.

**Blocked by:** 01 to 15: all other tickets

**Status:** ready-for-agent

Spec: [../spec.md](../spec.md)

- [ ] A search finds no `lvh-yard init`, `--reinit`, root `compose.yaml` or `dev/*.env` in text, comments, scripts or docs.
- [ ] The learning that says `init --upgrade` edits only image lines is replaced with `trail note --replaces`, and the old file is removed with `git rm` after trail saves the new one.
- [ ] `GLOSSARY.md`, the ADRs and `docs/user-guide.md` agree on the terms Yard CA, Workstation Certificate and Client Certificate.
- [ ] `mise run check` passes.
- [ ] `.scratch/ws-command/` is deleted, as `docs/agents/issue-tracker.md` says.
