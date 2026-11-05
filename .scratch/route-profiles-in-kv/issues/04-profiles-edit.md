# 04: `lvh-yard profiles edit`

**What to build:** You change one Route Profile as text in your terminal editor. See `.scratch/route-profiles-in-kv/spec.md`, user stories 33 to 36.

**Blocked by:** 03

**Status:** done

- [ ] `lvh-yard profiles edit <name>` writes the `profiles show` output to a temporary file and runs `$EDITOR` on it, else `vi`.
- [ ] If the text did not change, the command does nothing.
- [ ] If the text changed, the command sends the profile with `PUT` on its name.
- [ ] The command refuses a file with another name or with more than one profile.
- [ ] After a refusal, the command prints the reason and offers to edit again with the same text.
- [ ] The CLI tests set `$EDITOR` to a script that changes the file and to a script that leaves it as it is.
- [ ] `docs/user-guide.md` explains the command.
