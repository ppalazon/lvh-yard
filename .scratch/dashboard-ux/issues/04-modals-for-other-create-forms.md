# 04: Modals for Add an Alias, Bind a container and Upload a Backup

**What to build:** The Aliases, Containers and Backups pages show their lists first. Each create form opens in a modal, as on the Static Instances page, and comes back open after an input error. See `.scratch/dashboard-ux/spec.md`, user stories 9 to 11, 15 to 18.

**Blocked by:** 03 (First modal: Register a Static Instance).

**Status:** ready-for-agent

- [ ] The Aliases page has an "Add an Alias" button that opens the form in a modal.
- [ ] The Containers page opens its binding form in a modal. The CSS rule that shows only the fields of the chosen kind still works inside it.
- [ ] The Backups page has an "Upload a Backup" button that opens the upload form in a modal.
- [ ] Each modal uses the dialog path of ticket 03, with no new script.
- [ ] After an input error, each page comes back with its dialog marked as open, the input kept and the error shown.
- [ ] One-button actions such as Delete, Publish and Import stay where they are.
- [ ] Page tests cover each open button, each dialog and each open-after-error case.
- [ ] You try each modal by hand on `mise run dev:up`.
