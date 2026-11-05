# 03: The lvh-yard catalog command

**What to build:** `lvh-yard catalog` prints one line for each Service, grouped by Workstation, with state, hostname and the Instances count. It takes `--workstation` and `--state`. See `.scratch/catalog-per-service/spec.md`, user stories 24 to 31.

**Blocked by:** 01

**Status:** done

- [x] The command `catalog` sits next to `list`, with the description "Lists the Services of every Workstation, one line each."
- [x] Each Service gives one line in aligned columns: name, state, hostname, count.
- [x] `--workstation <name>` keeps one Workstation. `--state <state>` keeps one state.
- [x] An unknown state is refused with the list of states.
- [x] An unreachable Workstation gives `<name>: Workstation unreachable`.
- [x] The output has no Service Warnings and no Instance IDs.
- [x] `lvh-yard list` keeps its lines.
- [x] `docs/user-guide.md` lists `lvh-yard catalog` with its filters.
- [x] `YardTest` covers the command with and without filters, an unreachable Workstation and an unknown state.

## Comments

Implemented in `388e189`, merged in `54fb60b`. Spec review found a missing passing count. Fixed in `08a199e`, merged in `60b6746`: the CLI now uses N of M passing and No Instances. The full check passed in the fix worktree. Packaged CLI smoke checks covered both filters, invalid state values and the unchanged Instance lines in `list`.
