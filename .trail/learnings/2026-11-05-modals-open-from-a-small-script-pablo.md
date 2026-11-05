---
title: "Modals open from a small script, the form is the dialog panel, and Cancel is a plain button"
author: Pablo
date: 2026-11-05
branch: main
cost: { minutes: 30, tokens: 46052 }
id: l-c3ac39d5
scope: repo
status: active
replaces: l-27025f4a
src: agent
touches:
  - ".scratch/dashboard-ux/issues/01-phyxor-theme.md"
  - ".scratch/dashboard-ux/issues/02-external-links-in-a-new-tab.md"
  - ".scratch/dashboard-ux/issues/03-first-modal-static-instances.md"
  - ".scratch/dashboard-ux/issues/04-modals-for-other-create-forms.md"
  - ".scratch/dashboard-ux/issues/05-catalog-filters-on-change.md"
  - ".scratch/dashboard-ux/issues/06-service-warning-levels.md"
  - ".scratch/dashboard-ux/issues/07-service-page-alerts-and-risks.md"
  - ".scratch/dashboard-ux/issues/08-service-page-cards.md"
  - ".scratch/dashboard-ux/spec.md"
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/AliasesPage.java
---

## Decided
A create form sits in a native dialog. A small script opens it with showModal() when you click its open button, and on page load when the server marks it as shown after an input error.
The form itself is the dialog panel, with header, section and footer inside it. Basecoat's dialog style targets these elements, so the body scrolls and the footer gets its layout, and the form still posts as a normal page form.
One small record holds the dialog id and title. Each page names them once, and the title is also the label of the open button.

## Tried and ruled out
The native open attribute as the server mark. It shows the dialog without showModal(), so there is no backdrop, no Escape and no focus trap.
Cancel as a submit button with formmethod="dialog". The Enter key presses the first submit button in a form, and Cancel comes first in the footer, so Enter in a field would close the dialog instead of sending the form.
A section inside the form with the footer outside it. Basecoat's layout rules then did not match.

## Watch out
After a refused Backup upload the browser cannot refill the file field, so the user must choose the file again.

In the code:
- Dialogs.java: Dialogs.Modal(id, title[, description]), Dialogs.openButton, Dialogs.modal (sets the form class, method="post", Cancel and submit). A page adds its own form attributes through a callback, for example enctype or data-bind-form.
- static/dialogs.js: data-dialog-open="<id>", data-dialog-shown, data-dialog-close (Cancel is type="button").
