package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.function.Consumer;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Ul;

/**
 * The path of a request to a Service, drawn from this Workstation, with the facts that the picture
 * has no room for. It is built from settings and the Service alone: no request goes out.
 */
final class RouteDiagramPage {

  private RouteDiagramPage() {}

  static String path(Service service) {
    return ServicePage.path(service) + "/diagram";
  }

  static String render(
      Workstation workstation, Service service, TraefikSettings traefik, OutOfRange outOfRange) {
    return Layout.page(
        "Route Diagram · " + service.name(),
        workstation.name(),
        main ->
            main.div()
                .attrClass("route-diagram-page flex flex-col gap-5")
                .div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text("Route Diagram")
                .__()
                .div()
                .attrClass("flex flex-wrap gap-2")
                .a()
                .attrHref(Layout.attr(ServicePage.path(service)))
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text("Back to Service")
                .__()
                .a()
                .attrHref(Layout.attr(ReachabilityPage.path(service)))
                .attrClass("btn")
                .text("Test Reachability")
                .__()
                .__()
                .__()
                .p()
                .text(
                    service.name()
                        + " on "
                        + service.workstation()
                        + ", drawn from "
                        + workstation.name()
                        + ". It shows the setup, and sends no request.")
                .__()
                .of(page -> {
                  RouteDiagram diagram = RouteDiagram.of(workstation, service, traefik, outOfRange);
                  Layout.refreshed(
                      page,
                      "route-diagram",
                      path(service),
                      YARD_CHANGED,
                      region ->
                          region
                              .div()
                              .attrClass("route-diagram grid items-start gap-6 xl:grid-cols-2")
                              .div()
                              .attrClass("route-diagram-figure min-w-0")
                              .of(figure -> RouteDiagramSvg.draw(figure, diagram))
                              .__()
                              .div()
                              .attrClass("route-diagram-facts flex min-w-0 flex-col gap-5")
                              .of(facts -> facts(facts, diagram))
                              .__()
                              .__());
                })
                .__());
  }

  private static void facts(Div<?> facts, RouteDiagram d) {
    section(facts, "Listeners", body -> listeners(body, d));
    section(facts, "TLS", body -> tls(body, d));
    section(facts, "Route", body -> route(body, d));
    section(facts, "Route Profiles", body -> profiles(body, d));
    if (d.balancing() != null) {
      section(facts, "Load balancing", body -> balancing(body, d));
    }
    section(facts, "Yard", body -> yard(body, d));
  }

  private static void section(Div<?> facts, String title, Consumer<Div<?>> body) {
    facts
        .div()
        .attrClass("flex min-w-0 flex-col gap-1")
        .h2()
        .attrClass("text-base font-semibold")
        .text(title)
        .__()
        .of(div -> body.accept(div))
        .__();
  }

  private static void listeners(Div<?> body, RouteDiagram d) {
    if (d.http() == null) {
      body.p().text("This Workstation has no Traefik, so nothing listens for requests.").__();
      return;
    }
    listener(body, "HTTP", d.http(), d.httpServes(), httpNote(d));
    if (d.https() == null) {
      body.p().text("HTTPS is off. Run \"lvh-yard ws tls https create\" to turn it on.").__();
    } else {
      listener(body, "HTTPS", d.https(), d.httpsServes(), null);
    }
  }

  private static String httpNote(RouteDiagram d) {
    return d.clientCertificate() && d.noRoute() == null
        ? "A Route Profile needs a Client Certificate, so the Route answers only on HTTPS."
        : null;
  }

  private static void listener(
      Div<?> body, String name, RouteDiagram.Listener listener, boolean serves, String note) {
    body.p()
        .text(name + " listens on ")
        .code()
        .text(listener.address())
        .__()
        .text(
            ": "
                + listener.reach()
                + ". "
                + (serves ? "It serves this Route." : "It has no Route for this Service."))
        .__();
    if (note != null) {
      body.p().attrClass("text-muted-foreground").text(note).__();
    }
  }

  private static void tls(Div<?> body, RouteDiagram d) {
    body.p()
        .text(
            "Browser to Traefik: HTTP is plain text."
                + (d.https() == null ? "" : " HTTPS uses TLS."))
        .__();
    if (d.clientCertificate()) {
      body.p()
          .text("The browser must also show a Client Certificate that the Workstation CA signed.")
          .__();
    }
    body.p()
        .text(
            d.mutualTls()
                ? "Traefik to Instances: mutual TLS. Traefik and the Instance check each other's"
                    + " certificates. The Instance's certificate must name "
                    + d.service()
                    + "."
                : "Traefik to Instances: plain HTTP.")
        .__();
    if (d.remote()) {
      body.p()
          .attrClass("text-muted-foreground")
          .text("The Instances are on " + d.home() + ", so this step crosses the LAN.")
          .__();
    }
  }

  private static void route(Div<?> body, RouteDiagram d) {
    if (d.noRoute() != null) {
      body.p().text(d.noRoute()).__();
      return;
    }
    body.p().code().text(d.hostname()).__().text(" on " + d.viewer() + ".").__();
    body.p()
        .attrClass("text-muted-foreground")
        .text("Access log: " + (d.accessLog() ? "on" : "off") + ".")
        .__();
  }

  private static void profiles(Div<?> body, RouteDiagram d) {
    if (d.profiles().isEmpty()) {
      body.p().text("None. Requests go to the Instances as they are.").__();
      return;
    }
    body.p().attrClass("text-muted-foreground").text("In the order the Route applies them.").__();
    for (RouteProfileSummary profile : d.profiles()) {
      body.div()
          .attrClass("flex min-w-0 flex-col gap-1")
          .p()
          .attrClass("font-medium")
          .text(profile.name())
          .__()
          .of(div -> {
            if (profile.missing()) {
              div.p()
                  .text(
                      "The Home Workstation has no such Route Profile, so Traefik refuses the"
                          + " Route.")
                  .__();
            } else if (profile.middlewares().isEmpty()) {
              div.p().attrClass("text-muted-foreground").text("It has no middleware.").__();
            }
            profile.middlewares().forEach(middleware -> middleware(div, middleware));
          })
          .__();
    }
  }

  private static void middleware(Div<?> div, RouteProfileSummary.Middleware middleware) {
    div.div()
        .attrClass("flex min-w-0 flex-col")
        .p()
        .code()
        .text(middleware.type())
        .__()
        .text(middleware.authentication() ? " asks who the user is" : "")
        .__()
        .of(card -> {
          if (middleware.options().isEmpty()) {
            return;
          }
          Ul<?> list = card.ul().attrClass("route-diagram-options");
          middleware
              .options()
              .forEach(option ->
                  list.li()
                      .code()
                      .text(option.key())
                      .__()
                      .text(option.hidden() ? " (value hidden)" : ": ")
                      .of(item -> {
                        if (!option.hidden()) {
                          item.code().text(option.value()).__();
                        }
                      })
                      .__());
          list.__();
        })
        .__();
  }

  private static void balancing(Div<?> body, RouteDiagram d) {
    body.p().text(d.balancing()).__();
    body.p()
        .text(
            d.instancePriority()
                ? "Instance Priority is on: the Route uses only the first Instance Source with a"
                    + " passing Instance."
                : "Instance Priority is off: every passing Instance of every Instance Source"
                    + " shares the Route.")
        .__();
    body.p()
        .attrClass("text-muted-foreground")
        .text(
            "Consul checks decide which Instances pass, not Traefik. There are no sticky sessions.")
        .__();
  }

  private static void yard(Div<?> body, RouteDiagram d) {
    if (d.scope() == null) {
      body.p().text("Not published. No Workstation has a hostname for this Service.").__();
    } else if (d.scope() == Scope.LOCAL) {
      body.p().text("Local Scope: only " + d.home() + " has a hostname for this Service.").__();
    } else if (d.remote()) {
      body.p()
          .text(
              "Yard Scope: "
                  + d.home()
                  + " shares this Service. This Workstation sends requests straight to the"
                  + " Instances that other Workstations can reach.")
          .__();
    } else {
      body.p()
          .text("Yard Scope: every Workstation gets the hostname ")
          .code()
          .text(d.yardHostname())
          .__()
          .text(
              ", with its own Base Domain. Its Traefik sends requests straight to these Instances.")
          .__();
      if (d.skippedByYard() > 0) {
        body.p()
            .attrClass("text-risk")
            .text(
                "Other Workstations skip the Instances at a loopback or Docker address, because"
                    + " only "
                    + d.home()
                    + " can connect to them.")
            .__();
      }
    }
  }
}
