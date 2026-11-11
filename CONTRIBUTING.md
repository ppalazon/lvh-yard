# Contributing

Thank you for your interest in LVH Yard. This page tells you how to send a
change.

## Before you start

- For a bug or a small fix, open a pull request.
- For a new feature or a big change, open an
  [issue](https://github.com/ppalazon/lvh-yard/issues) first. We agree on the
  idea before you write the code.
- Read [GLOSSARY.md](GLOSSARY.md). It defines the words that code, docs and
  pages use, such as Yard, Workstation and Service. Use them, and not the words
  that the glossary says to avoid.
- Read the [ADRs](docs/adr/) that touch the area you change.

## Set up

[docs/development.md](docs/development.md) lists what you need and shows how to
run LVH Yard from the sources. In short, install Docker with Compose, JDK 25
and [mise](https://mise.jdx.dev/). Then run:

```sh
mise run hooks          # installs the pre-commit hook
mise run test:unit      # a few seconds, no Docker
mise run check          # everything, before you open the pull request
```

The pre-commit hook formats your files and scans your changes for secrets with
gitleaks. `mise run secrets` scans the whole history.

## Rules for a change

- `mise run check` must pass.
- A change in behavior comes with a test. [docs/development.md](docs/development.md)
  explains the three kinds of tests.
- If your change affects how people install or use LVH Yard, update
  [docs/user-guide.md](docs/user-guide.md). If it affects building, testing or
  releasing, update [docs/development.md](docs/development.md).
- Write all text in simple English: CLI help, messages, page text, docs and
  code comments. Use short sentences, active voice, and the condition before the
  command. `CLAUDE.md` has the full rules.
- Keep one idea in each commit, and say in the message what it does.

## AI-assisted changes

You may use AI tools. Say in the pull request if AI wrote part of the change.
You must understand the code and be able to explain it. You answer for the
change, not the tool.

## License

LVH Yard is licensed under the Apache License, Version 2.0. When you send a
change, you license it under the same terms. There is no CLA and no sign-off to
add.

By taking part, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
