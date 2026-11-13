package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.TraefikSettings;
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
    String http = httpUrl(settings, service);
    String https = httpsUrl(settings, service);
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

  /** The plain HTTP address of a Publication, or null when it has no hostname or needs HTTPS. */
  private static String httpUrl(TraefikSettings settings, Service service) {
    if (service.hostname() == null || service.requiresClientCertificate()) {
      return null;
    }
    return "http://" + service.hostname() + (settings.port() == 80 ? "" : ":" + settings.port());
  }

  /** The HTTPS address of a Publication, or null when it has no hostname or this has no HTTPS. */
  private static String httpsUrl(TraefikSettings settings, Service service) {
    if (service.hostname() == null || !settings.enabled() || settings.httpsPort() == null) {
      return null;
    }
    return "https://"
        + service.hostname()
        + (settings.httpsPort() == 443 ? "" : ":" + settings.httpsPort());
  }
}
