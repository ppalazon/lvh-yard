# 01: lvh-yard init writes a ready Workstation folder

**What to build:** A user who downloaded only the `lvh-yard` executable runs `lvh-yard init --workstation ws-a` and gets a `compose.yaml` and `.env` they can start with `docker compose up -d`, with every value LVH Yard can find filled in, and the next steps printed. See `.scratch/init/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `init` writes `compose.yaml` and `.env` from templates inside the CLI to `~/.config/lvh-yard/` (or `$XDG_CONFIG_HOME/lvh-yard/`), or to `--dir`
- [x] The templates are the repository's own `compose.yaml` and `.env.example`; a test fails when they drift
- [x] Flags `--workstation`, `--peers`, `--gossip-key`, `--base-domain` fill `.env`; the Workstation name is checked with the dashboard's rule
- [x] Without `--gossip-key`, a new key is made (32 random bytes, Base64) and printed with a reminder that every Workstation needs the same one
- [x] `YARD_UID`, `YARD_GID` and `YARD_DOCKER_GID` are filled from this computer; a missing Docker socket is a warning, not a failure
- [x] `.env` sets `COMPOSE_PROFILES=dashboard`; the dashboard image is pinned to the CLI's version (`latest` for a snapshot build)
- [x] Existing files are never overwritten unless `--force`; exit 1 with the file named
- [x] A missing required value with no terminal is a usage error (exit 2) naming the flag
- [x] The next steps are printed: ports to open, `docker compose up -d` in the folder, the dashboard's address
- [x] A test runs `docker compose config` on the written folder
- [x] The CLI tests pass against the native executable, templates included
