package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.reachability.TraefikSettings;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.ServiceState;
import org.xmlet.htmlapifaster.PhrasingContent;

final class ServiceViews {

  private ServiceViews() {}

  static void state(PhrasingContent<?, ?> parent, ServiceState state) {
    String variant =
        switch (state) {
          case HEALTHY -> "success";
          case DEGRADED -> "risk";
          case UNHEALTHY -> "destructive";
          case UNAVAILABLE -> "outline";
          case UNPUBLISHED -> "secondary";
        };
    parent.span().attrClass("badge").addAttr("data-variant", variant).text(label(state)).__();
  }

  static String label(ServiceState state) {
    return switch (state) {
      case HEALTHY -> "Healthy";
      case DEGRADED -> "Degraded";
      case UNHEALTHY -> "Unhealthy";
      case UNAVAILABLE -> "Unavailable";
      case UNPUBLISHED -> "Unpublished";
    };
  }

  /** No link when this Workstation cannot serve a certificate-only Route. */
  static void hostname(PhrasingContent<?, ?> parent, Service service, TraefikSettings settings) {
    if (service.hostname() == null) {
      return;
    }
    String url = settings.publicationUrl(service);
    if (url == null) {
      parent.text(service.hostname());
    } else {
      Layout.externalLink(parent, service.hostname(), url);
    }
  }
}
