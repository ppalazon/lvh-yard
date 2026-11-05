// Dialogs.java writes the dialogs and their buttons. Loaded with defer, so the page is parsed when this runs.
document.addEventListener("click", (event) => {
  const opener = event.target.closest("[data-dialog-open]");
  if (opener) {
    document.getElementById(opener.dataset.dialogOpen)?.showModal();
    return;
  }
  event.target.closest("[data-dialog-close]")?.closest("dialog")?.close();
});

for (const dialog of document.querySelectorAll("dialog[data-dialog-shown]")) {
  dialog.showModal();
}
