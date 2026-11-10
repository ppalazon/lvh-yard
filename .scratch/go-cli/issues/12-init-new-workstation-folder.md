# 12: `init` for a new Workstation Folder

Status: done

Blocked by: 03

Spec: `../spec.md`.

## What to build

`lvh-yard init` writes the Workstation Folder that `docker compose up -d` starts. `init` does not call the dashboard.

- `init` asks only for the Workstation name and the Base Domain at a terminal. Enter takes the host name made into a name, and `lvh.me`. Without a terminal, `--workstation` is required. It also takes `--base-domain`, `--peers` and `--gossip-key`. One reader reads every answer, and when the input ends early, `init` fails.
- `init` does not overwrite a file unless `--force` is given.
- `init` finds the Docker group and fills it only when the setting is empty.
- The templates: committed copies of the repo's `compose.yaml` and `.env.example`, embedded with `go:embed`. A task, `mise run cli:templates`, refreshes the copies from the repo root. A unit test fails when a copy differs from its root file. The compose copy has no `build: .`, and its dashboard image is pinned to the version of the CLI, or `latest` for `dev`.
- The Workstation Folder lookup (`YARD_CONFIG_DIR`, `$XDG_CONFIG_HOME/lvh-yard`, `~/.config/lvh-yard`), from ticket 02.

Follow `Init.java`, `Templates.java`, `EnvFile.java` and `ConfigFolder.java`. Port the cases of `InitTest` that test a new folder, a refused overwrite, `--force`, the prompts and the input that ends early.

## Acceptance criteria

- [x] `lvh-yard init --workstation ws-a --base-domain lvh.me` in a clean home writes `compose.yaml` and `.env`, and `docker compose config` accepts them.
- [x] Without a terminal and without `--workstation`, `init` fails and writes nothing.
- [x] A second `init` refuses to overwrite, and `--force` replaces the files.
- [x] The drift test fails when a template copy is edited.
- [ ] `mise run check` passes.

## Comments

- 2026-11-10: The integration owner reported that `mise run test:unit` passed, including Java, Go and template tests. The Go build also passed.
- With an isolated `HOME`, `init` wrote the default Workstation Folder, and `docker compose config` accepted the generated files. A development build used the `latest` dashboard image and removed the dashboard build line.
- Live overwrite refusal and `--force` passed. Nonterminal input without the required flag and input that ended early both left no files.
- A real PTY run displayed both prompts and exited with code 0.
- The integration owner deliberately edited a template copy and observed the drift test fail, then restored the copy.
- `mise run check` and hosted checks remain unverified. No tests, builds, linters or formatters ran during integration.
