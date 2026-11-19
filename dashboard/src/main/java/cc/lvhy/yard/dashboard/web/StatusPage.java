package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.STATUS_CHANGED;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.status.Part;
import cc.lvhy.yard.dashboard.status.PartState;
import cc.lvhy.yard.dashboard.status.Status;
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
                STATUS_CHANGED,
                region ->
                    region
                        .div()
                        .attrClass("flex flex-col gap-2")
                        .div()
                        .attrClass("flex flex-wrap items-center gap-3")
                        .h1()
                        .text("Status")
                        .__()
                        .of(heading -> StatusViews.badge(heading, status.overall()))
                        .__()
                        .__()
                        .div()
                        .attrClass("status-parts min-w-0 divide-y rounded-lg border")
                        .of(content -> status.parts().forEach(part -> part(content, part)))
                        .__()));
  }

  private static void part(Div<?> content, Part part) {
    content
        .div()
        .attrId(StatusViews.partId(part.name()))
        .attrClass("min-w-0 p-5 wrap-anywhere")
        .div()
        .attrClass("mb-2 flex flex-wrap items-center justify-between gap-3")
        .h2()
        .text(part.name())
        .__()
        .of(heading -> StatusViews.badge(heading, part.state()))
        .__()
        .p()
        .text(part.reason())
        .__()
        .of(card -> {
          if (!part.tls().isEmpty()) {
            tls(card, part);
          }
        })
        .of(card -> {
          if (!part.details().isEmpty()) {
            details(card, part);
          }
        })
        .__();
  }

  /** The lock and one badge for each TLS that the part uses. */
  private static void tls(Div<?> card, Part part) {
    card.div()
        .attrClass("mt-3 flex flex-wrap items-center gap-2")
        .of(row -> {
          Layout.lockIcon(row.svg(), "size-4 text-muted-foreground");
          row.span().attrClass("sr-only").text("Uses TLS:").__();
          part.tls()
              .forEach(name ->
                  row.span().attrClass("badge").addAttr("data-variant", "outline").text(name).__());
        })
        .__();
  }

  private static void details(Div<?> card, Part part) {
    card.custom("details")
        .attrClass("status-details mt-4")
        .addAttr("data-preserve-open", "status:" + part.name() + ":" + part.state())
        .of(box -> {
          if (part.state() != PartState.OK) {
            box.addAttr("open", "");
          }
        })
        .custom("summary")
        .addAttr("aria-label", part.name() + " technical details")
        .text("Technical details")
        .__()
        .section()
        .attrClass("mt-2")
        .table()
        .attrClass("table status-details-table")
        .caption()
        .attrClass("sr-only")
        .text(part.name() + " technical details")
        .__()
        .tbody()
        .of(body ->
            part.details()
                .forEach(detail ->
                    body.tr()
                        .th()
                        .addAttr("scope", "row")
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
        .__()
        .__();
  }
}
