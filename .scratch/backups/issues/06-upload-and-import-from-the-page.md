# 06: Upload and import from the Backups page

**What to build:** You upload a Backup from your browser and import any Backup from the page in two steps. See `.scratch/backups/spec.md`, user stories 48 to 50 and 52 to 54.

**Blocked by:** 03, 05

**Status:** done

- [x] An upload keeps its file name.
- [x] The page refuses a name with a path separator, a name that exists, and a file that is not a Backup.
- [x] Import on a row asks for the mode, then shows the dry-run plan.
- [x] After you confirm, the page imports and shows the result and the safety Backup name.
- [x] A file that does not parse cannot be imported.
- [x] Page tests cover upload and its refusals, both import steps, both modes and the result.
- [x] `docs/user-guide.md` explains upload and import from the page.
