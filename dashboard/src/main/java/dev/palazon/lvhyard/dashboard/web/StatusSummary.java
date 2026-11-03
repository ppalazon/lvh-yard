package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.status.Part;
import dev.palazon.lvhyard.dashboard.status.Status;
import htmlflow.HtmlFlow;
import org.xmlet.htmlapifaster.A;

/**
 * The state shows as a symbol as well as a colour. The header loads the dots through HTMX, so a
 * slow part never slows the page.
 */
final class StatusSummary {

  static final String PATH = StatusPage.PATH + "/summary";

  private StatusSummary() {}

  static String render(Status status) {
    StringBuilder out = new StringBuilder();
    HtmlFlow.doc(out)
        .setIndented(false)
        .div()
        .a()
        .attrHref(StatusPage.PATH)
        .attrClass("flex items-center gap-1")
        .of(link -> status.parts().forEach(part -> dot(link, part)))
        .__()
        .__();
    return out.toString();
  }

  private static void dot(A<?> link, Part part) {
    StatusViews.Look look = StatusViews.look(part.state());
    String label = part.name() + ": " + look.label() + ". " + part.reason();
    link.span()
        .attrId("dot-" + StatusViews.slug(part.name()))
        .attrClass(
            "inline-flex size-5 items-center justify-center rounded-full text-xs font-bold "
                + look.dotColour())
        .addAttr("role", "img")
        .addAttr("aria-label", Layout.attr(label))
        .addAttr("title", Layout.attr(label))
        .text(look.symbol())
        .__();
  }
}
