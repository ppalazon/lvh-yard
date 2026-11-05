# 03: `lvh-yard profiles show`, `set` and `remove`

**What to build:** From the terminal, you print a Route Profile in Traefik's file format, apply a file of profiles, and remove a profile. See `.scratch/route-profiles-in-kv/spec.md`, user stories 27 to 32, 37 and 38.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `lvh-yard profiles show <name>` prints a YAML document with `http.middlewares.<name>`.
- [ ] `lvh-yard profiles set <file>` sends every profile of the file as one set. `-` reads standard input.
- [ ] The output of `profiles show` works as input to `profiles set`.
- [ ] If the dashboard refuses the set, the command prints each reason and changes nothing.
- [ ] `lvh-yard profiles remove <name>` removes a profile. If the profile is in use, it prints the conflict with the Services and chains.
- [ ] Each refusal gives a non-zero exit code.
- [ ] The CLI tests with the stub dashboard cover each command, standard input and the exit codes.
- [ ] `docs/user-guide.md` explains the three commands.
