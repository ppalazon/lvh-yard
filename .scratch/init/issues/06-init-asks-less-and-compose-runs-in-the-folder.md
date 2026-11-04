# 06: init asks less, and lvh-yard compose runs in the Workstation Folder

**What to build:** A new user runs `lvh-yard init`, presses Enter twice, and gets a working Workstation. Joining a Yard comes later. `lvh-yard compose` runs `docker compose` in the Workstation Folder from any directory.

**Blocked by:** 02, 04

**Status:** done

## Decisions

- At a terminal, init offers the hostname of the computer as the Workstation name. Init takes the part before the first dot, makes it lowercase, and turns each invalid character into `-`. Then it trims `-` at both ends and cuts the name to 63 characters. The question shows the default in brackets, and Enter accepts it. If nothing valid is left, init asks with no default.
- Without a terminal, `--workstation` stays required. Two teammates can have the same hostname, and a silent default is easy to miss.
- Init no longer asks for Peers. The `--peers` flag stays. The next steps say how to join a Yard later: `lvh-yard init --reinit --peers <ip> --gossip-key <key>`.
- At a terminal, init asks for the Base Domain with `lvh.me` as the default. If the answer is not a domain, init asks again. `--base-domain` and `--reinit` skip the question. Without a terminal, `lvh.me` stays the silent default.
- Consul starts with `-disable-keyring-file`. Otherwise Consul keeps the first gossip key in its data volume and ignores a new key from `--reinit`.
- `lvh-yard compose <args...>` has no options of its own. It passes every argument to `docker compose`, in the folder from `YARD_CONFIG_DIR`, else `$XDG_CONFIG_HOME/lvh-yard`, else `~/.config/lvh-yard`. It passes Docker's exit code through.
- If the folder has no `compose.yaml`, `lvh-yard compose` exits 1 with `No compose.yaml in <folder>: run lvh-yard init first.` If Docker cannot start, it exits 1 with `Could not run docker: <reason>.`
- The next steps of init say `lvh-yard compose up -d`. For a `--dir` folder that `lvh-yard compose` does not find by itself, they say `YARD_CONFIG_DIR=<dir> lvh-yard compose up -d`.

## Acceptance

- [x] At a terminal, Enter on the name question takes the cleaned hostname
- [x] A hostname with no valid character gives a question with no default
- [x] Init never asks for Peers, and `--peers` still sets them
- [x] At a terminal, Enter on the Base Domain question keeps `lvh.me`, and an invalid domain is asked again
- [x] `--reinit` asks for neither the name nor the Base Domain
- [x] The Consul command in `compose.yaml` has `-disable-keyring-file`
- [x] `lvh-yard compose` runs Docker in the Workstation Folder with the given arguments and its exit code
- [x] `lvh-yard compose` explains a missing `compose.yaml`
- [x] The next steps name `lvh-yard compose up -d` and the way to join a Yard later
- [x] `docs/user-guide.md` describes the new questions, `lvh-yard compose` and joining later
