# Coding standards

The review reads this file. A rule that a tool can check lives in a test or a hook, not here.

## Checked by tests

Do not review these by eye. A failing test names the rule.

- `cli/internal/command/help_commands_test.go`: a command in help text or output stands alone on its own line, or sits in double quotes.
- `cli/internal/command/help_text_test.go`: `Short` length, third person and period. `Long` labels and their order. Flag usage length. No `mise` in help text.

## Docs

- A change that affects how people install or use LVH Yard updates `docs/user-guide.md`.
- A change that affects building, testing or releasing updates `docs/development.md`.
- `README.md` and the landing page in `docs/site/` are short. They link to the user guide and do not repeat it. A change updates them only when it alters what LVH Yard is or how it installs. The maintainer reviews them.

## Writing

Write all text in simple English: CLI help, messages, page text, API docs, docs, code comments and configuration comments. Use short sentences, active voice, and the condition before the command.

- CLI text and page text says only what a thing does, in one short line.
- A code comment says only what the code cannot: why, a hidden rule, or a value the type does not show.
- CLI text and dashboard text may name `docker compose` and `lvh-yard` commands, for example to point at `lvh-yard ws compose logs consul`. Neither names `mise` tasks, because Workstations run LVH Yard without this repo.

## CLI text

This applies to CLI help, output, prompts and errors.

- `Long`: one line of purpose, then the blocks that apply. Items are two-space indented lines. Steps are numbered `1.`, facts start with `-`.
- Flag usage: one clause, such as "Sets ...", "Reads ...". Write a default as `(default: ...)` at the end.
- Output: the success line first, then `label: value` lines or a `Next:` list. Do not repeat what the command just did.
- Errors: "problem. What to do." Name the exact command or flag.
- A command that the user runs stands alone on its own indented line, exactly as typed. Inside a sentence, put it in double quotes, for example `Run "lvh-yard ws init" first.`
- A change must keep machine-read output as it is: Backups, JSON, list columns, the `ws show` lines.
