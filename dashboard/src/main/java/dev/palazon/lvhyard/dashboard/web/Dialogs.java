package dev.palazon.lvhyard.dashboard.web;

import java.util.function.Consumer;
import org.xmlet.htmlapifaster.Dialog;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Form;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Section;

/**
 * Modals: native {@code <dialog>} elements with Basecoat's dialog style. {@code dialogs.js} opens
 * one with {@code showModal()} on a click of its {@link #openButton}, and on page load when the
 * server marks it as shown, such as after a form post with an input error. The native dialog closes
 * on Escape and moves the focus into itself.
 */
final class Dialogs {

  /**
   * @param title also the label of the open button
   * @param description one line under the title, or {@code null}
   */
  record Modal(String id, String title, String description) {

    Modal(String id, String title) {
      this(id, title, null);
    }
  }

  private Dialogs() {}

  static void openButton(Element<?, ?> parent, Modal modal) {
    parent
        .custom("button")
        .addAttr("type", "button")
        .addAttr("class", "btn w-fit")
        .addAttr("data-dialog-open", modal.id())
        .text(modal.title())
        .__();
  }

  /**
   * The form is the dialog's panel, so Basecoat lays out its header, its section, which scrolls,
   * and its footer, which holds Cancel and {@code submit}.
   *
   * @param shown whether the page opens it on load
   * @param form adds the form's action and any other attribute; the form posts
   * @param fields writes the form's fields
   */
  static <Z extends Element> void modal(
      Main<Z> main,
      Modal modal,
      boolean shown,
      Consumer<Form<Dialog<Main<Z>>>> form,
      Consumer<Section<Form<Dialog<Main<Z>>>>> fields,
      String submit) {
    main.dialog()
        .attrId(modal.id())
        .attrClass("dialog")
        .addAttr("aria-labelledby", modal.id() + "-title")
        .of(dialog -> {
          if (modal.description() != null) {
            dialog.addAttr("aria-describedby", modal.id() + "-description");
          }
          if (shown) {
            dialog.addAttr("data-dialog-shown", "true");
          }
        })
        .form()
        .attrClass("form")
        .addAttr("method", "post")
        .of(form)
        .header()
        .h2()
        .attrId(modal.id() + "-title")
        .text(modal.title())
        .__()
        .of(header -> {
          if (modal.description() != null) {
            header.p().attrId(modal.id() + "-description").text(modal.description()).__();
          }
        })
        .__()
        .section()
        .attrClass("flex flex-col gap-6 overflow-y-auto")
        .of(fields)
        .__()
        .footer()
        .button()
        .attrType(EnumTypeButtonType.BUTTON)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-dialog-close", "true")
        .text("Cancel")
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .text(submit)
        .__()
        .__()
        .__()
        .__();
  }
}
