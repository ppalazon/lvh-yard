# 04: `ws show`, `ws set` and `ws join`

**What to build:** The user changes settings without editing `.env`. `ws show` prints them, `ws set` changes one, and `ws join` moves this Workstation to another Yard. `--reinit` is removed, because these commands replace it. There is no `ws rename`.

**Blocked by:** 02: `ws init` and `ws upgrade` replace `init`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `ws show` prints the settings of `.env`, and the Gossip Key is hidden.
- [x] `ws set <name> <value>` takes a name from a fixed list: Base Domain, Peers, the three bind addresses, each port, the out-of-range ranges and the stack-trace flag. It checks the value with the rules `ws init` has, and changes one line, with the comments left as they are.
- [x] `ws set` refuses an invalid value and says what is valid. It never restarts anything and prints the next step.
- [x] `ws set` and `ws join` refuse a change of the Workstation name, and the message says to export a Backup, run `ws init` under the new name and import it.
- [x] `ws join --gossip-key <key> [--peers <list>]` checks the key like `ws init` does and changes the key and the Peers. `--peers` takes spaces or commas.
- [x] `ws init --reinit` no longer exists, and its tests move to `ws set` and `ws join` or are deleted.
- [x] CLI unit tests cover valid and invalid values for each kind of name, the kept comments, and the hidden key. `docs/user-guide.md` has the commands.
