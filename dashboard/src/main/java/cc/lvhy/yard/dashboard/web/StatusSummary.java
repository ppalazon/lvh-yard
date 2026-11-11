package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.status.Part;
import cc.lvhy.yard.dashboard.status.Status;
import htmlflow.HtmlFlow;
import org.xmlet.htmlapifaster.PhrasingContent;

/**
 * The state shows as a symbol as well as a colour. The header loads the summary through HTMX, so a
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
        .attrClass("inline-flex min-h-9 items-center gap-3 rounded-sm text-sm")
        .span()
        .attrClass("inline-flex items-center gap-2 md:hidden")
        .text("Status")
        .of(label -> StatusViews.badge(label, status.overall()))
        .__()
        .span()
        .attrClass("hidden items-center gap-3 md:inline-flex")
        .of(parts ->
            status
                .parts()
                .forEach(part ->
                    parts
                        .span()
                        .attrClass("inline-flex items-center gap-1.5")
                        .span()
                        .addAttr("aria-hidden", "true")
                        .text(part.name())
                        .__()
                        .of(label -> dot(label, part))
                        .__()))
        .__()
        .__()
        .__();
    return out.toString();
  }

  private static void dot(PhrasingContent<?, ?> link, Part part) {
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
