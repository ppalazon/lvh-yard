package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.status.PartState;
import org.xmlet.htmlapifaster.PhrasingContent;

/**
 * What the status page, and anything else that shows a part's state, shares: its label, the symbol
 * that tells it apart without colour, and the badge.
 */
final class StatusViews {

  private StatusViews() {}

  /** The state as the pages name it, such as {@code Warning}. */
  static String label(PartState state) {
    return switch (state) {
      case OK -> "OK";
      case WARNING -> "Warning";
      case FAILING -> "Failing";
    };
  }

  /** A symbol for the state, so it reads without colour: a tick, an exclamation mark or a cross. */
  static String symbol(PartState state) {
    return switch (state) {
      case OK -> "✓";
      case WARNING -> "!";
      case FAILING -> "✕";
    };
  }

  /** The Basecoat badge variant for the state. */
  static String variant(PartState state) {
    return switch (state) {
      case OK -> "primary";
      case WARNING -> "secondary";
      case FAILING -> "destructive";
    };
  }

  /** A badge with the state's symbol and label, such as {@code ✕ Failing}. */
  static void badge(PhrasingContent<?, ?> parent, PartState state) {
    parent
        .span()
        .attrClass("badge")
        .addAttr("data-variant", variant(state))
        .text(symbol(state) + " " + label(state))
        .__();
  }

  /**
   * The HTML id of the part called {@code name}: {@code Core Services} is {@code
   * part-core-services}.
   */
  static String partId(String name) {
    return "part-" + name.toLowerCase().replace(' ', '-');
  }
}
