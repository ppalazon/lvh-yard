package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.status.PartState;
import org.xmlet.htmlapifaster.PhrasingContent;

final class StatusViews {

  private StatusViews() {}

  /**
   * @param symbol so the state reads without colour
   * @param dotColour written out whole, so that Tailwind builds it
   */
  record Look(String label, String symbol, String variant, String dotColour) {}

  static Look look(PartState state) {
    return switch (state) {
      case OK -> new Look("OK", "✓", "primary", "bg-green-600 text-white");
      case WARNING -> new Look("Warning", "!", "secondary", "bg-amber-400 text-black");
      case FAILING -> new Look("Failing", "✕", "destructive", "bg-red-600 text-white");
    };
  }

  static void badge(PhrasingContent<?, ?> parent, PartState state) {
    Look look = look(state);
    parent
        .span()
        .attrClass("badge")
        .addAttr("data-variant", look.variant())
        .text(look.symbol() + " " + look.label())
        .__();
  }

  /** {@code Core Services} is {@code core-services}. */
  static String slug(String name) {
    return name.toLowerCase().replace(' ', '-');
  }

  static String partId(String name) {
    return "part-" + slug(name);
  }
}
