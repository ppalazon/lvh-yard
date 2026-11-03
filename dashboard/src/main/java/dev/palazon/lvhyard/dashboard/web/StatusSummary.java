package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.status.Part;
import dev.palazon.lvhyard.dashboard.status.PartState;
import dev.palazon.lvhyard.dashboard.status.Status;
import htmlflow.HtmlFlow;
import org.xmlet.htmlapifaster.A;

/**
 * The status dots every page's header shows: one per part, with the part's state as a symbol as
 * well as a colour, and its name and reason on hover. They link to the {@link StatusPage}. The
 * header loads them through HTMX, so a slow part never slows the page.
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

  /** One part's dot, such as a red cross labelled {@code Docker: Failing. There is no…}. */
  private static void dot(A<?> link, Part part) {
    String label = part.name() + ": " + StatusViews.label(part.state()) + ". " + part.reason();
    link.span()
        .attrId("dot-" + StatusViews.partId(part.name()).substring("part-".length()))
        .attrClass(
            "inline-flex size-5 items-center justify-center rounded-full text-xs font-bold "
                + colour(part.state()))
        .addAttr("role", "img")
        .addAttr("aria-label", Layout.attr(label))
        .addAttr("title", Layout.attr(label))
        .text(StatusViews.symbol(part.state()))
        .__();
  }

  /** The dot's colours, written out whole so that Tailwind builds them. */
  private static String colour(PartState state) {
    return switch (state) {
      case OK -> "bg-green-600 text-white";
      case WARNING -> "bg-amber-400 text-black";
      case FAILING -> "bg-red-600 text-white";
    };
  }
}
