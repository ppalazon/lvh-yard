# 02: lvh-yard init asks for missing values in a terminal

**What to build:** Run in a terminal without flags, `init` asks for the Workstation name and the peers, so a new user needs no docs.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] With a terminal attached, `init` prompts for each missing value that has a flag
- [ ] An invalid Workstation name is explained and asked again
- [ ] Empty peers are accepted (the first Workstation of a Yard)
- [ ] Without a terminal, no prompt is ever shown
