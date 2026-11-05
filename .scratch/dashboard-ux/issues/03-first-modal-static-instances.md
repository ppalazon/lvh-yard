# 03: First modal: Register a Static Instance

**What to build:** The Static Instances page shows its list first. A "Register a Static Instance" button opens the form in a modal. If the input is wrong, the page comes back with the modal open, the input kept and the error under the field. See `.scratch/dashboard-ux/spec.md`, user stories 7, 8, 12 to 17 and 19.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] The modal is a native `<dialog>` with Basecoat's dialog style. It has a title, the form, a submit button and a Cancel button that closes it.
- [ ] The page shell has a few lines of script. A button with a data attribute that names a dialog opens it with `showModal()`.
- [ ] On page load, the script opens with `showModal()` each dialog that the server marks as open.
- [ ] The Escape key closes the modal. On open, the focus moves into the modal. The native dialog gives both.
- [ ] The form posts as it does today. HTMX is not part of the modal.
- [ ] After a post with an input error, the server marks the dialog as open. The dialog keeps the input and shows the error under its field.
- [ ] The Edit Instance page stays a page.
- [ ] Page tests cover the open button and the closed dialog that holds the form. They also cover the open dialog after an input error.
- [ ] You try the modal by hand on `mise run dev:up`: open, Cancel, Escape, and the error case.
