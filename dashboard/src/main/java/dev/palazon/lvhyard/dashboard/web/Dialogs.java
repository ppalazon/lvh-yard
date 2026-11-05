package dev.palazon.lvhyard.dashboard.web;

import java.util.function.Consumer;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Form;
import org.xmlet.htmlapifaster.Main;

/**
 * Modals: native {@code <dialog>} elements with Basecoat's dialog style. {@code dialogs.js} opens
 * one with {@code showModal()} on a click of its {@link #openButton}, and on page load when the
 * server marks it as shown, such as after a form post with an input error. The native dialog closes
 * on Escape and moves the focus into itself.
 */
final class Dialogs {

  private Dialogs() {}

  static void openButton(Element<?, ?> parent, String id, String label) {
    parent
        .custom("button")
        .addAttr("type", "button")
        .addAttr("class", "btn w-fit")
        .addAttr("data-dialog-open", id)
        .text(label)
        .__();
  }

  /**
   * @param shown whether the page opens it on load
   * @param body writes the dialog's form; {@link #actions} ends it
   */
  static void modal(Main<?> main, String id, String title, boolean shown, Consumer<Div<?>> body) {
    main.dialog()
        .attrId(id)
        .attrClass("dialog")
        .addAttr("aria-labelledby", id + "-title")
        .of(dialog -> {
          if (shown) {
            dialog.addAttr("data-dialog-shown", "true");
          }
        })
        .div()
        .attrClass("overflow-y-auto")
        .header()
        .h2()
        .attrId(id + "-title")
        .text(title)
        .__()
        .__()
        .of(div -> body.accept(div))
        .__()
        .__();
  }

  /** Cancel closes the dialog, without sending the form. */
  static void actions(Form<?> form, String submit) {
    form.footer()
        .attrClass("flex flex-col-reverse gap-2 sm:flex-row sm:justify-end")
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
        .__();
  }
}
