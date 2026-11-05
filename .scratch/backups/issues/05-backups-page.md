# 05: The Backups page

**What to build:** The dashboard has a Backups page where you see, create, download and delete Backups. See `.scratch/backups/spec.md`, user stories 43 to 47, 51 and 55.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] The navigation bar has a Backups link, highlighted on the page.
- [ ] The page lists the `.yaml` and `.yml` files of the Backups folder. Each row shows the name, the date from `exported-from`, and the number of Services, Aliases and Route Profiles.
- [ ] A file that does not parse shows its error in the list.
- [ ] A button creates a Backup named `<workstation>-<yyyyMMdd-HHmmss>.yaml`.
- [ ] Each row has a download link and a delete button that asks for a confirmation.
- [ ] The dashboard never deletes a Backup by itself.
- [ ] Page tests cover the list, a broken file, create, download, delete and the navigation link.
- [ ] `docs/user-guide.md` explains the page.
