package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.FlowContent;

/** What a list page shows in place of its table when the list is empty. It says what to do next. */
final class EmptyState {

  private EmptyState() {}

  /**
   * @param title what is empty, such as {@code No Backups yet.}
   * @param help the next step, in one or two sentences
   */
  static void render(FlowContent<?, ?> parent, String title, String help) {
    parent
        .div()
        .attrClass(
            "flex flex-col items-center gap-1 rounded-lg border border-dashed px-4 py-10"
                + " text-center")
        .p()
        .attrClass("font-medium")
        .text(title)
        .__()
        .p()
        .attrClass("text-muted-foreground max-w-prose")
        .text(help)
        .__()
        .__();
  }
}
