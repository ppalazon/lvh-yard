---
title: "Modals open from dialogs.js, and Cancel is a plain button"
author: Pablo
date: 2026-11-05
branch: worktree-agent-a39f201cda66cf5c3
cost: { minutes: 9, tokens: 49596 }
id: l-27025f4a
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/StaticInstancesPage.java
---

Decided: a create form sits in a native dialog from Dialogs.modal in Dialogs.java. dialogs.js opens it with showModal() on a click of a button with data-dialog-open="<id>", and on page load for a dialog with data-dialog-shown, which the server sets after a post with an input error. Ruled out: the native open attribute for the server mark. It shows the dialog without showModal(), so there is no backdrop, no Escape and no focus trap. Ruled out: Cancel as a submit button with formmethod="dialog". In a form, the Enter key presses the first submit button, and Cancel comes first in the footer of Dialogs.actions, so Enter in a field would close the dialog instead of sending the form. Cancel is type="button" with data-dialog-close.
