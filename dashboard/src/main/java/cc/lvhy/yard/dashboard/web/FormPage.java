package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import java.util.function.Consumer;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.Form;

/**
 * A create form on a page of its own, such as {@code /services/new}. The list page links to it with
 * {@link #openLink}. Cancel is a link back to the list page, so it needs no script, and it also
 * works after a post with an input error, when the browser history holds the form itself.
 */
final class FormPage {

  /**
   * @param section the link of the navigation bar that is current, or {@code null} for none
   * @param title the page heading, and also the label of the link that opens the page
   * @param description one line under the title, or {@code null}
   * @param path where the page is
   * @param action where the form posts
   * @param submit the label of the submit button
   */
  record Spec(
      Layout.Section section,
      String title,
      String description,
      String path,
      String action,
      String submit) {}

  private FormPage() {}

  static void openLink(Element<?, ?> parent, Spec spec) {
    parent
        .custom("a")
        .addAttr("href", Layout.attr(spec.path()))
        .addAttr("class", "btn w-fit")
        .text(spec.title())
        .__();
  }

  /** A small outline link, for a row of actions in a card. */
  static void openCardLink(Element<?, ?> parent, Spec spec) {
    parent
        .custom("a")
        .addAttr("href", Layout.attr(spec.path()))
        .addAttr("class", "btn w-fit")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .text(spec.title())
        .__();
  }

  /**
   * The submit button comes before Cancel, so the Enter key in a field sends the form.
   *
   * @param form adds any other attribute of the form; the form posts to the spec's action
   * @param fields writes the form's fields
   */
  static String render(
      Workstation workstation,
      Spec spec,
      Consumer<Form<?>> form,
      Consumer<FlowContent<?, ?>> fields) {
    return render(workstation, spec, spec.section().path, form, fields);
  }

  /**
   * @param cancel where Cancel goes, for a form page that the Section's page does not open
   */
  static String render(
      Workstation workstation,
      Spec spec,
      String cancel,
      Consumer<Form<?>> form,
      Consumer<FlowContent<?, ?>> fields) {
    return render(workstation, spec, cancel, true, form, fields);
  }

  /**
   * @param submittable {@code false} when the form has nothing to send, so its submit button is off
   */
  static String render(
      Workstation workstation,
      Spec spec,
      String cancel,
      boolean submittable,
      Consumer<Form<?>> form,
      Consumer<FlowContent<?, ?>> fields) {
    return Layout.page(
        spec.title(),
        workstation.name(),
        spec.section(),
        main ->
            main.div()
                .attrClass("flex max-w-xl flex-col gap-2")
                .h1()
                .text(spec.title())
                .__()
                .of(content -> {
                  if (spec.description() != null) {
                    content.p().attrClass("text-muted-foreground").text(spec.description()).__();
                  }
                })
                .__()
                .form()
                .attrClass("form card flex max-w-xl flex-col gap-6 overflow-visible p-6")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(spec.action()))
                .of(form::accept)
                .of(fields::accept)
                .div()
                .attrClass("flex gap-3")
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .of(button -> button.attrDisabled(!submittable))
                .text(spec.submit())
                .__()
                .a()
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .attrHref(Layout.attr(cancel))
                .text("Cancel")
                .__()
                .__()
                .__());
  }
}
