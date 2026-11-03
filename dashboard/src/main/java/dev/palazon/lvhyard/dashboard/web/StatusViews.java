package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.status.PartState;
import org.xmlet.htmlapifaster.PhrasingContent;

/**
 * What the status page, the status dots, and anything else that shows a part's state, share: how
 * each state looks, the badge, and the ids made from a part's name.
 */
final class StatusViews {

  private StatusViews() {}

  /**
   * How a state looks.
   *
   * @param label the state as the pages name it, such as {@code Warning}
   * @param symbol a symbol, so it reads without colour: a tick, an exclamation mark or a cross
   * @param variant the Basecoat badge variant
   * @param dotColour the status dot's colours, written out whole so that Tailwind builds them
   */
  record Look(String label, String symbol, String variant, String dotColour) {}

  /** How {@code state} looks. */
  static Look look(PartState state) {
    return switch (state) {
      case OK -> new Look("OK", "✓", "primary", "bg-green-600 text-white");
      case WARNING -> new Look("Warning", "!", "secondary", "bg-amber-400 text-black");
      case FAILING -> new Look("Failing", "✕", "destructive", "bg-red-600 text-white");
    };
  }

  /** A badge with the state's symbol and label, such as {@code ✕ Failing}. */
  static void badge(PhrasingContent<?, ?> parent, PartState state) {
    Look look = look(state);
    parent
        .span()
        .attrClass("badge")
        .addAttr("data-variant", look.variant())
        .text(look.symbol() + " " + look.label())
        .__();
  }

  /**
   * The part called {@code name} as it goes in an id: {@code Core Services} is {@code
   * core-services}.
   */
  static String slug(String name) {
    return name.toLowerCase().replace(' ', '-');
  }

  /**
   * The HTML id of the part called {@code name} on the status page, such as {@code part-consul}.
   */
  static String partId(String name) {
    return "part-" + slug(name);
  }
}
