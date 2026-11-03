package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
import org.xmlet.htmlapifaster.PhrasingContent;

/** How the pages show a Service's state and hostname. */
final class ServiceViews {

  private ServiceViews() {}

  /**
   * The state as a badge: plain when published and healthy, red when unhealthy, muted otherwise.
   */
  static void state(PhrasingContent<?, ?> parent, ServiceState state) {
    String variant =
        switch (state) {
          case HEALTHY -> "primary";
          case UNHEALTHY -> "destructive";
          case UNAVAILABLE -> "outline";
          case UNPUBLISHED -> "secondary";
        };
    parent.span().attrClass("badge").addAttr("data-variant", variant).text(label(state)).__();
  }

  /**
   * A muted badge that marks a Core Service, which LVH Yard registers and keeps itself, after a
   * space; nothing for any other Service.
   */
  static void core(PhrasingContent<?, ?> parent, Service service) {
    if (service.core()) {
      parent.text(" ");
      parent
          .span()
          .attrClass("badge")
          .addAttr("data-variant", "outline")
          .attrTitle("LVH Yard registers and keeps it on every Workstation.")
          .text("Core Service")
          .__();
    }
  }

  /** The state as the pages name it, such as {@code Unhealthy}. */
  static String label(ServiceState state) {
    return switch (state) {
      case HEALTHY -> "Healthy";
      case UNHEALTHY -> "Unhealthy";
      case UNAVAILABLE -> "Unavailable";
      case UNPUBLISHED -> "Unpublished";
    };
  }

  /**
   * A link to the Service's hostname, when this Workstation has a Route for it; nothing otherwise.
   */
  static void hostname(PhrasingContent<?, ?> parent, Service service) {
    if (service.hostname() == null) {
      return;
    }
    parent
        .a()
        .attrHref(Layout.attr("http://" + service.hostname()))
        .attrClass("underline")
        .text(service.hostname())
        .__();
  }
}
