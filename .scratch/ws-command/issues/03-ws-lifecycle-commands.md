# 03: `ws start`, `stop`, `restart` and `logs`

**What to build:** From any directory, `lvh-yard ws start` starts Consul, Traefik and the dashboard of this Workstation. `stop`, `restart` and `logs` work the same way. Each command prints the `docker compose` line it runs, so that the user learns it and can run it alone. The process seam of the CLI gets a method that runs a command, and the fake records it.

**Blocked by:** 02: `ws init` and `ws upgrade` replace `init`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `ws start` runs `docker compose up -d` with the Workstation Folder as the project directory, and prints that line first.
- [x] `ws stop` and `ws restart` run `docker compose stop` and `docker compose restart` in the same way.
- [x] `ws logs` shows the logs of every service. `ws logs consul`, `traefik` or `dashboard` shows one. `-f` follows.
- [x] `ws logs` with another name fails and lists the valid names, before it runs anything.
- [x] The exit code of `docker compose` is the exit code of `ws`.
- [x] When `docker` is not on the PATH, the command fails with a message that says so.
- [x] The commands do not call the dashboard, and they work while it is down.
- [x] CLI unit tests with the fake process cover the recorded line, the project directory, the service names, the exit code and the missing `docker`. `docs/user-guide.md` has the commands.
