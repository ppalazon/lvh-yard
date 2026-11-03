# Set up a Workstation with lvh-yard init

Status: ready-for-agent

Words follow `GLOSSARY.md`. Decisions follow ADR-0001 (one Consul datacenter per Workstation) and ADR-0002 (Traefik reads its Routes from LVH Yard). System packages (`.deb`, `.rpm`, AUR, Homebrew) are not built in this repository.

## Problem Statement

A person who downloads the `lvh-yard` executable from a release has no `compose.yaml` and no `.env`. Today they must clone the repository, copy `.env.example`, run `docker run consul keygen` for a gossip key, look up their user, group and Docker socket group, and guess the next steps from the README. When a new release comes out, they do not know how to move their files to the new images or the new options without losing their settings.

## Solution

`lvh-yard init` writes a ready `compose.yaml`, `.env` and `route-profiles.yaml` from templates inside the executable, into `~/.config/lvh-yard/` or `--dir`. It fills in every value it can find itself, makes the gossip key, asks for the Workstation name and peers when a terminal is attached (or takes them as flags), and ends by printing the next steps. `lvh-yard init --upgrade` changes only the image versions in an existing `compose.yaml`. `lvh-yard init --reinit` backs up the old files and writes new ones from the latest templates, keeping the values already set. The CLI then finds the dashboard through the `.env` that `init` wrote.

## User Stories

1. As a new user, I want `lvh-yard init` to write `compose.yaml` and `.env` for me, so that I do not need to clone the repository.
2. As a new user, I want the files written to `~/.config/lvh-yard/` by default, so that I know where my Workstation's setup lives.
3. As a user, I want `--dir <path>` to write the files somewhere else, so that I can keep them with my other Compose projects.
4. As a new user, I want `init` to ask for my Workstation name when it runs in a terminal, so that I do not have to read the docs first.
5. As a new user, I want `init` to refuse a Workstation name that is not lowercase letters, digits and `-`, so that Consul and the hostnames accept it.
6. As a new user, I want `init` to ask for the peers' LAN addresses, and accept none, so that the first Workstation of a Yard can start alone.
7. As a user scripting a setup, I want `--workstation`, `--peers`, `--gossip-key` and `--base-domain` flags, so that `init` runs without prompts.
8. As a user scripting a setup, I want `init` never to prompt when no terminal is attached, and to fail with a clear message naming the missing flag, so that a script never hangs.
9. As the first user of a Yard, I want `init` to make the gossip key itself, so that I do not need Docker to run `consul keygen`.
10. As a user joining a Yard, I want `--gossip-key` to take the Yard's existing key, so that my Workstation can join the others.
11. As a user joining a Yard, I want `init` to remind me that every Workstation needs the same gossip key, and print mine, so that I can share it with the next one.
12. As a user, I want `init` to fill `YARD_UID`, `YARD_GID` and `YARD_DOCKER_GID` from this computer, so that the dashboard can read the Docker socket without root.
13. As a user, I want `init` to warn me when it cannot find the Docker socket, so that I know the dashboard will not follow containers.
14. As a user without Java, I want `.env` to set `COMPOSE_PROFILES=dashboard`, so that `docker compose up -d` also starts the dashboard from its image.
15. As a user, I want the dashboard image in `compose.yaml` pinned to the same version as my `lvh-yard` executable, so that the CLI and the API always match.
16. As a user, I want `init` to write a `route-profiles.yaml` with commented examples, so that I can add Route Profiles later without looking up the format.
17. As a user, I want `init` to refuse to overwrite existing files, so that I never lose my settings by running it twice.
18. As a user, I want `--force` to overwrite existing files, so that I can start over on purpose.
19. As a new user, I want `init` to print the next steps: which ports to open to the other Workstations, `docker compose up -d` in that folder, and the dashboard's address, so that I know how to start the Yard.
20. As a new user, I want the next steps to tell me how to check that everything runs, with `lvh-yard status`, so that I know when I am done.
21. As a user, I want every setting in `.env` to keep its comment from the template, so that I understand what I can change.
22. As a user upgrading the CLI, I want `lvh-yard init --upgrade` to change only the image versions in my `compose.yaml`, so that my own changes to it stay.
23. As a user upgrading, I want `--upgrade` to leave `.env` and `route-profiles.yaml` untouched, so that my settings stay.
24. As a user upgrading, I want `--upgrade` to print each image it changed, from and to, so that I can see what moved.
25. As a user upgrading, I want `--upgrade` to say when an image line was not found, so that I know a hand-edited file was not changed.
26. As a user who wants the latest options, I want `lvh-yard init --reinit` to back up my `compose.yaml` and `.env` as `<name>.<date-time>.bak` and write new ones, so that I get new options and can go back.
27. As a user who reinits, I want every value I set in the old `.env` carried into the new one, so that my Workstation keeps its name, key, peers and ports.
28. As a user who reinits, I want settings that are new in this version to take their default, and settings that no longer exist kept at the end as comments, so that nothing I wrote is lost silently.
29. As a user who reinits, I want `route-profiles.yaml` left untouched, so that my Route Profiles stay.
30. As a user, I want `lvh-yard` to find the dashboard from the `.env` that `init` wrote (its port and bind address), so that I do not need `--dashboard` after changing the port.
31. As a user, I want `--dashboard` and `YARD_DASHBOARD_URL` to still win over that `.env`, so that I can point the CLI elsewhere.
32. As a user with files in another folder, I want `YARD_CONFIG_DIR` to tell the CLI where `init` wrote them, so that it finds that `.env`.

## Implementation Decisions

- New `init` subcommand of the `lvh-yard` command. It needs no dashboard and never talks to one.
- Templates for `compose.yaml`, `.env` and `route-profiles.yaml` are resources inside the CLI, made from the repository's own `compose.yaml` and `.env.example`, so they cannot drift. A build step or test keeps them the same. The native executable must include them as resources.
- The dashboard image tag in the template is the CLI's own version, the same one `lvh-yard --version` prints. A snapshot build falls back to `latest`.
- The config folder is `~/.config/lvh-yard/` (or `$XDG_CONFIG_HOME/lvh-yard/`), else `--dir`, else `YARD_CONFIG_DIR` when reading.
- Values found by the CLI: `YARD_UID` and `YARD_GID` from the current user, `YARD_DOCKER_GID` from the group of `/var/run/docker.sock`. The gossip key is 32 random bytes, Base64-encoded, the same format as `consul keygen`.
- Prompts only when the console is interactive. Each prompt has a flag. Without a terminal, a missing required value is a usage error (exit 2) naming the flag.
- Workstation name rule: the same one the dashboard enforces (a single DNS label, lowercase).
- `--upgrade` rewrites only the `image:` values of the `consul`, `traefik` and `dashboard` services, keeping each line's form otherwise. It edits text by line; it does not reparse and reformat the YAML.
- `--reinit` backs up `compose.yaml` and `.env` to `<file>.<yyyyMMdd-HHmmss>.bak`, writes the new templates, and copies over each `KEY=value` from the old `.env` whose key exists in the new one. Keys that no longer exist are appended under a comment saying they are no longer used.
- `--upgrade`, `--reinit` and `--force` are mutually exclusive.
- The CLI's dashboard address order becomes: `--dashboard`, `YARD_DASHBOARD_URL`, the `.env` in the config folder (`YARD_DASHBOARD_BIND_ADDRESS`, with `0.0.0.0` read as `127.0.0.1`, and `YARD_DASHBOARD_PORT`), then `http://127.0.0.1:7780`.
- The `compose.yaml` template also carries what the other specs need (Traefik's admin entrypoint and ping, and the `route-profiles.yaml` mount); this spec only writes what the template holds.
- Exit codes: 0 on success, 1 on a file problem (exists without `--force`, cannot write), 2 on usage errors.

## Testing Decisions

- A good test runs `lvh-yard` with arguments and checks only what a user could see: its output, its exit code and the files in the folder. It never calls internal classes.
- Seam: the CLI, through the existing CLI test. `init` tests run against a temporary folder and need no stub dashboard. The tests that check how the dashboard address is found use the existing WireMock stub dashboard.
- Tested: files written and their filled values; prompts skipped without a terminal; refusal to overwrite and `--force`; `--upgrade` changing only image lines, and reporting a missing line; `--reinit` backups, carried values, new defaults and kept old keys; the next steps printed; the dashboard address order.
- One test runs `docker compose config` on the generated folder, so the templates stay valid Compose files.
- The CLI tests run again against the native executable (`mise run native`), so the templates must be found there too.
- Prior art: the existing CLI test and its stub dashboard.

## Out of Scope

- System packages, and installing the executable.
- Starting the containers: `init` only writes files and prints the next steps.
- Changing an existing `.env` value from the CLI (other than through `--reinit`).
- Generating TLS certificates.

## Further Notes

- The README's "Running on a Workstation" moves to `lvh-yard init` as the first path; the repository path (`mise run up`) stays for developers.
