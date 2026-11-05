# 03: The lvh-yard catalog command

**What to build:** `lvh-yard catalog` prints one line for each Service, grouped by Workstation, with state, hostname and the Instances count. It takes `--workstation` and `--state`. See `.scratch/catalog-per-service/spec.md`, user stories 24 to 31.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] The command `catalog` sits next to `list`, with the description "Lists the Services of every Workstation, one line each."
- [ ] Each Service gives one line in aligned columns: name, state, hostname, count.
- [ ] `--workstation <name>` keeps one Workstation. `--state <state>` keeps one state.
- [ ] An unknown state is refused with the list of states.
- [ ] An unreachable Workstation gives `<name>: Workstation unreachable`.
- [ ] The output has no Service Warnings and no Instance IDs.
- [ ] `lvh-yard list` keeps its lines.
- [ ] `docs/user-guide.md` lists `lvh-yard catalog` with its filters.
- [ ] `YardTest` covers the command with and without filters, an unreachable Workstation and an unknown state.
