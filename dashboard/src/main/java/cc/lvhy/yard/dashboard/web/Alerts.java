package cc.lvhy.yard.dashboard.web;

import org.xmlet.htmlapifaster.FlowContent;

/**
 * A Basecoat alert whose icon and title are the summary of a {@code details} element, and whose
 * description opens under it.
 */
final class Alerts {

  private static final String CIRCLE = "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0";

  /** The look of an alert, and whether its description starts open. */
  enum Level {

    /** What stops the hostname from working. A screen reader reads it at once. */
    ERROR("destructive", "alert", true, CIRCLE, "M12 8v4", "M12 16h.01"),

    /** A problem that the hostname works despite. */
    RISK(
        "risk",
        null,
        false,
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01"),

    /** A fact to know. */
    NOTE(null, null, false, CIRCLE, "M12 16v-4", "M12 8h.01");

    private final String variant;
    private final String role;
    private final boolean open;
    private final String[] icon;

    Level(String variant, String role, boolean open, String... icon) {
      this.variant = variant;
      this.role = role;
      this.open = open;
      this.icon = icon;
    }
  }

  private Alerts() {}

  /**
   * @param id the id of the alert. It also keys the open state, so disclosures.js can restore it
   *     after each refresh. It must be the same on each refresh and differ from the other alerts.
   * @param topic the title, a few words
   * @param message the description
   */
  static void render(
      FlowContent<?, ?> parent, Level level, String id, String topic, String message) {
    parent
        .div()
        .attrClass("alert")
        .attrId(id)
        .of(alert -> {
          if (level.variant != null) {
            alert.addAttr("data-variant", level.variant);
          }
          if (level.role != null) {
            alert.addAttr("role", level.role);
          }
        })
        // custom: HtmlFlow's details gives its summary no attributes and its section no children.
        .custom("details")
        .attrClass("alert-details")
        .addAttr("data-preserve-open", id)
        .of(details -> {
          if (level.open) {
            details.addAttr("open", "");
          }
        })
        .custom("summary")
        .of(summary -> {
          Layout.icon(summary.svg(), "size-4 shrink-0", level.icon);
          summary.h2().text(topic).__();
        })
        .__()
        .section()
        .text(message)
        .__()
        .__()
        .__();
  }
}
