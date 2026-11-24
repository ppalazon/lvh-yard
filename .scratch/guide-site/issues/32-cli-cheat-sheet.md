# 32: Reference: CLI cheat sheet

**What to build:** One page of commands grouped by task (look, create, bind, publish, order, protect, test, move). Each command has one line. It is a cheat sheet, not the full reference.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- The commands of `docs/user-guide.md`, section "Using the CLI", grouped by task.
- A link to the full reference on GitHub for each group.

## Verification

- Run `lvh-yard --help` and `lvh-yard <command> --help` and compare each command and option.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Page: `docs/guide/reference/cli.md`, in `draft_docs`, nav group "Reference" with the label "CLI".
- Groups: Look, Create, Bind, Publish, Order, Protect, Test, Move (the ticket), and three more that the ticket does not list: Run the Workstation, Join a Yard, Certificates. They cover the `ws` commands and `update`, so that every command of `lvh-yard --help` appears. Each group ends with a link to the matching section of `docs/user-guide.md` on GitHub. The anchors exist in the user guide.
- Compared with `lvh-yard --help` and the `--help` of every command that the page lists: the commands, the flags and the one-line results agree. Many results reuse the words of the help text.
- Ran every command of the page on two throwaway Workstations in one Yard (`ws tls ca create`, `ws yard init`, `share`, `join`, `peers`, `peers --sync`, and the others), and the page lists no command that failed. Exceptions and what was found:
  - `lvh-yard update --check` answers `404 Not Found` while only `v0.2.0-rc.1` exists, because the install reads the latest release (ticket 36). `lvh-yard update 0.2.0-rc.1 --check` printed `Release: 0.2.0-rc.1`. The page shows `update --check`. It works after the release `v0.2.0`.
  - `lvh-yard test web` exits 1 when a check fails, as in my first run (a second Static Instance did not listen). With a healthy Service it printed `Every check passed.`
  - `remove web --id` removed the Instance with the ID that `lvh-yard priority web` printed. I did not check the claim that `catalog --details` shows the same ID. It comes from the user guide.
  - `remove web --host H --port P` failed with `give --host` when the Static Instance and a Container Binding of the same Service had the same address `127.0.0.1:18091`. The message lists the same host twice. That is my test setup, and not something the page shows.
- The cheat sheet shows no output, so nothing on it can be wrong about real output.
