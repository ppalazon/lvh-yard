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

  private static final String RELEASES_URL = "https://github.com/ppalazon/lvh-yard/releases";

  /** Lucide's tag icon: the outline, and the hole of its string as a dot. */
  private static final String TAG_SHAPE =
      "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704"
          + " 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z";

  private static final String TAG_HOLE = "M7.5 7.5h.01";

  private StatusPage() {}

  /** {@code v0.2.0}, as the release tag reads. A version that is not a number stays as it is. */
  private static String label(String version) {
    return !version.isEmpty() && Character.isDigit(version.charAt(0)) ? "v" + version : version;
  }

  static String render(Workstation workstation, Status status, String version) {
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
                        .a()
                        .attrId("dashboard-version")
                        .attrHref(RELEASES_URL)
                        .attrTarget("_blank")
                        .addAttr("rel", "noopener noreferrer")
                        .attrClass("badge tabular-nums")
                        .addAttr("data-variant", "outline")
                        .addAttr(
                            "aria-label",
                            "Dashboard version "
                                + version
                                + ". Releases on GitHub (opens in a new tab)")
                        .attrTitle("Dashboard version " + version + ". See the releases on GitHub.")
                        .of(link -> Layout.icon(link.svg(), "", TAG_SHAPE, TAG_HOLE))
                        .text(label(version))
                        .__()
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
        .attrClass("min-w-0 p-4 wrap-anywhere")
        .div()
        .attrClass("mb-1 flex flex-wrap items-center justify-between gap-3")
        .div()
        .attrClass("flex flex-wrap items-center gap-2")
        .h2()
        .text(part.name())
        .__()
        .of(title -> StatusViews.tlsBadges(title, part))
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
    card.custom("details")
        .attrClass("status-details mt-3")
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
        .attrClass("mt-1")
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
