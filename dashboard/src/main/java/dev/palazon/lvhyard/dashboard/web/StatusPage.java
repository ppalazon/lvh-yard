package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.status.Part;
import dev.palazon.lvhyard.dashboard.status.Status;
import org.xmlet.htmlapifaster.Div;

/** It refreshes itself, so a fix shows as it takes effect. */
final class StatusPage {

  static final String PATH = "/status";

  private StatusPage() {}

  static String render(Workstation workstation, Status status) {
    return Layout.page(
        "Status",
        workstation.name(),
        main ->
            Layout.refreshed(
                main,
                "status",
                PATH,
                region ->
                    region
                        .div()
                        .attrClass("flex flex-wrap items-center gap-3")
                        .h1()
                        .text("Status")
                        .__()
                        .of(heading -> StatusViews.badge(heading, status.overall()))
                        .__()
                        .p()
                        .text("Whether LVH Yard works on " + workstation.name() + ".")
                        .__()
                        .of(content -> status.parts().forEach(part -> part(content, part)))));
  }

  private static void part(Div<?> content, Part part) {
    content
        .div()
        .attrId(StatusViews.partId(part.name()))
        .attrClass("card flex flex-col gap-3 p-4")
        .div()
        .attrClass("flex flex-wrap items-center gap-3")
        .h2()
        .text(part.name())
        .__()
        .of(heading -> StatusViews.badge(heading, part.state()))
        .__()
        .p()
        .text(part.reason())
        .__()
        .of(card -> {
          if (!part.details().isEmpty()) {
            details(card, part);
          }
        })
        .__();
  }

  private static void details(Div<?> card, Part part) {
    card.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .tbody()
        .of(body ->
            part.details()
                .forEach(detail ->
                    body.tr()
                        .th()
                        .attrClass("font-medium")
                        .text(detail.name())
                        .__()
                        .td()
                        .text(detail.value())
                        .__()
                        .td()
                        .of(cell -> {
                          if (detail.state() != null) {
                            StatusViews.badge(cell, detail.state());
                          }
                        })
                        .__()
                        .__()))
        .__()
        .__()
        .__();
  }
}
