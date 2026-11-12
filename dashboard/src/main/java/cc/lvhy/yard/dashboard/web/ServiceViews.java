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

  /**
   * The hostname as a link. With HTTPS on this Workstation, a lock icon links to the HTTPS address
   * next to the HTTP link. A Publication that needs a Client Certificate has only the HTTPS link.
   * With no address that this Workstation serves, it is plain text.
   */
  static void hostname(PhrasingContent<?, ?> parent, Service service, TraefikSettings settings) {
    if (service.hostname() == null) {
      return;
    }
    String http = settings.httpUrl(service);
    String https = settings.httpsUrl(service);
    if (http != null) {
      Layout.externalLink(parent, service.hostname(), http);
      if (https != null) {
        Layout.secureIconLink(parent, https);
      }
    } else if (https != null) {
      Layout.secureLink(parent, service.hostname(), https);
    } else {
      parent.text(service.hostname());
    }
  }
}
