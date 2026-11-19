package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.status.Part;
import cc.lvhy.yard.dashboard.status.PartState;
import java.util.List;
import java.util.Map;
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
      case OK -> new Look("OK", "✓", "success", "bg-green-700 text-white");
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

  /**
   * The TLS that each part can have, in the order the page shows them. The names are the ones that
   * ConsulStatus and TraefikStatus put in {@code Part.tls}.
   */
  private static final Map<String, List<String>> TLS_OF =
      Map.of(
          "Consul", List.of("TLS"),
          "Traefik", List.of("HTTPS", "Client Certificate", "Mutual TLS"));

  /**
   * A green badge for each TLS that the part uses, and a red one for each that it does not. A part
   * that does not answer shows none, because it cannot say.
   */
  static void tlsBadges(PhrasingContent<?, ?> parent, Part part) {
    if (part.state() == PartState.FAILING && part.details().isEmpty()) {
      return;
    }
    for (String name : TLS_OF.getOrDefault(part.name(), List.of())) {
      boolean on = part.tls().contains(name);
      parent
          .span()
          .attrClass("badge")
          .addAttr("data-variant", on ? "success" : "destructive")
          .span()
          .addAttr("aria-hidden", "true")
          .text(on ? "✓" : "✕")
          .__()
          .text(" " + name)
          .span()
          .attrClass("sr-only")
          .text(on ? ": on" : ": off")
          .__()
          .__();
    }
  }

  static String slug(String name) {
    return name.toLowerCase().replace(' ', '-');
  }

  static String partId(String name) {
    return "part-" + slug(name);
  }
}
