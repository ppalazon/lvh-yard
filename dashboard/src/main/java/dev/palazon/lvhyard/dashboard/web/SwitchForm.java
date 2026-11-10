package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.FlowContent;

/**
 * A Basecoat switch in its own form. The switch has no name, as an unchecked box sends nothing. A
 * hidden field sends the value that the flip makes, and {@code switches.js} posts the form.
 */
final class SwitchForm {

  private SwitchForm() {}

  /**
   * @param action where the form posts
   * @param field the name of the hidden field, or {@code null} when the action alone says it
   * @param value what the field sends: the state that the flip gives
   * @param on whether the switch shows on now
   * @param label what the switch does, in a few words
   * @param hint one line under the label, or {@code null}
   */
  static void render(
      FlowContent<?, ?> parent,
      String action,
      String field,
      String value,
      boolean on,
      String label,
      String hint) {
    parent
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(action))
        .attrClass("switch-form")
        .of(form -> {
          if (field != null) {
            form.input()
                .attrType(EnumTypeInputType.HIDDEN)
                .attrName(field)
                .attrValue(Layout.attr(value))
                .__();
          }
        })
        .label()
        .attrClass("label gap-3")
        .input()
        .attrType(EnumTypeInputType.CHECKBOX)
        .addAttr("role", "switch")
        .attrClass("input")
        .addAttr("autocomplete", "off")
        .of(input -> {
          if (on) {
            input.addAttr("checked", "");
          }
        })
        .__()
        .text(label)
        .__()
        .of(form -> {
          if (hint != null) {
            form.p().attrClass("text-muted-foreground text-sm").text(hint).__();
          }
        })
        .__();
  }
}
