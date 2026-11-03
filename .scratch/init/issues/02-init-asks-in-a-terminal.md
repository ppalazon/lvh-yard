# 02: lvh-yard init asks for missing values in a terminal

**What to build:** Run in a terminal without flags, `init` asks for the Workstation name and the peers, so a new user needs no docs.

**Blocked by:** 01

**Status:** done

- [x] With a terminal attached, `init` prompts for each missing value that has a flag
- [x] An invalid Workstation name is explained and asked again
- [x] Empty peers are accepted (the first Workstation of a Yard)
- [x] Without a terminal, no prompt is ever shown
