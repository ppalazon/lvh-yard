# 02: `ws init` and `ws upgrade` replace `init`

**What to build:** `lvh-yard ws` exists as a group, with one persistent `--dir` flag. `lvh-yard ws init` does what `lvh-yard init` did, and `lvh-yard ws upgrade` does what `lvh-yard init --upgrade` did. The old top-level `init` is gone, with no alias. `--reinit` moves to `ws init` for now. Ticket 04 removes it. Behaviour, questions, checks and Gossip Key rules do not change.

**Blocked by:** 01: Move the template files to `config/`

**Status:** ready-for-agent

Spec: [../spec.md](../spec.md)

- [ ] `lvh-yard ws init` writes the same files with the same questions, flags and checks as `lvh-yard init` did. It still needs `--workstation` without a terminal.
- [ ] `lvh-yard ws upgrade` changes only the image lines of `compose.yaml`, byte for byte, and refuses when a service has no image line.
- [ ] `--dir` is one persistent flag of `ws`, and every `ws` subcommand uses it. The default folder is the one the CLI finds today.
- [ ] `lvh-yard init` is not a command. Help, shell completions and the CLI parity test have no `init`.
- [ ] No text says `lvh-yard init`: error messages, the compose and env comments, `docs/user-guide.md`, the README, the landing page and the install script all say `ws init` or `ws upgrade`.
- [ ] The tests of `init`, `--upgrade` and `--reinit` run as `ws init`, `ws upgrade` and `ws init --reinit`.
