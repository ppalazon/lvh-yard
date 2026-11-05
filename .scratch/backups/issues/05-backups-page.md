# 05: The Backups page

**What to build:** The dashboard has a Backups page where you see, create, download and delete Backups. See `.scratch/backups/spec.md`, user stories 43 to 47, 51 and 55.

**Blocked by:** 04

**Status:** done

- [x] The navigation bar has a Backups link, highlighted on the page.
- [x] The page lists the `.yaml` and `.yml` files of the Backups folder. Each row shows the name, the date from `exported-from`, and the number of Services, Aliases and Route Profiles.
- [x] A file that does not parse shows its error in the list.
- [x] A button creates a Backup named `<workstation>-<yyyyMMdd-HHmmss>.yaml`.
- [x] Each row has a download link and a delete button that asks for a confirmation.
- [x] The dashboard never deletes a Backup by itself.
- [x] Page tests cover the list, a broken file, create, download, delete and the navigation link.
- [x] `docs/user-guide.md` explains the page.
