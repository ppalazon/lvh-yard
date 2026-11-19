package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.ArrayList;
import java.util.List;
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
        service.name() + " - Route Diagram",
        workstation.name(),
        main ->
            main.div()
                .attrClass("route-diagram-page flex flex-col gap-5")
                .div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text(service.name() + " - Route Diagram")
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
                        + ", as "
                        + workstation.name()
                        + " sees it. No request is sent.")
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
                              .attrClass(
                                  hasFacts(diagram)
                                      ? "route-diagram grid items-start gap-6 xl:grid-cols-2"
                                      : "route-diagram")
                              .div()
                              .attrClass("route-diagram-figure min-w-0")
                              .of(figure -> RouteDiagramSvg.draw(figure, diagram))
                              .__()
                              .of(grid -> {
                                if (hasFacts(diagram)) {
                                  grid.div()
                                      .attrClass("route-diagram-facts flex min-w-0 flex-col gap-5")
                                      .of(facts -> facts(facts, diagram))
                                      .__();
                                }
                              })
                              .__());
                })
                .__());
  }

  /** The notes that the diagram has no room for. Each section appears only when it has content. */
  private static boolean hasFacts(RouteDiagram d) {
    return !tlsNotes(d).isEmpty() || !detailed(d).isEmpty() || d.balancing() != null;
  }

  private static void facts(Div<?> facts, RouteDiagram d) {
    List<String> tls = tlsNotes(d);
    if (!tls.isEmpty()) {
      section(facts, "TLS", body -> tls.forEach(note -> body.p().text(note).__()));
    }
    if (!detailed(d).isEmpty()) {
      section(facts, "Route Profiles", body -> profiles(body, d));
    }
    if (d.balancing() != null) {
      section(facts, "Load balancing", body -> balancing(body, d));
    }
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

  private static List<String> tlsNotes(RouteDiagram d) {
    List<String> notes = new ArrayList<>();
    if (d.http() != null && d.https() == null) {
      notes.add("To turn HTTPS on, run \"lvh-yard ws tls https create\".");
    }
    if (d.clientCertificate()) {
      notes.add("The browser must also show a Client Certificate signed by the Workstation CA.");
    }
    if (d.mutualTls()) {
      notes.add("The certificate of each Instance must name " + d.service() + ".");
    }
    return notes;
  }

  /** The Route Profiles with a middleware that has options or asks who the user is. */
  private static List<RouteProfileSummary> detailed(RouteDiagram d) {
    return d.profiles().stream()
        .filter(profile -> profile.middlewares().stream().anyMatch(RouteDiagramPage::hasDetail))
        .toList();
  }

  private static boolean hasDetail(RouteProfileSummary.Middleware middleware) {
    return middleware.authentication() || !middleware.options().isEmpty();
  }

  private static void profiles(Div<?> body, RouteDiagram d) {
    for (RouteProfileSummary profile : detailed(d)) {
      body.div()
          .attrClass("flex min-w-0 flex-col gap-1")
          .p()
          .attrClass("font-medium")
          .text(profile.name())
          .__()
          .of(div ->
              profile.middlewares().stream()
                  .filter(RouteDiagramPage::hasDetail)
                  .forEach(middleware -> middleware(div, middleware)))
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
    body.p()
        .text(
            d.instancePriority()
                ? "Instance Priority is on: only the first Instance Source with a passing Instance"
                    + " gets requests."
                : "Instance Priority is off: every passing Instance of every Instance Source gets"
                    + " requests.")
        .__();
    body.p()
        .attrClass("text-muted-foreground")
        .text("Consul checks decide which Instances pass. There are no sticky sessions.")
        .__();
  }
}
