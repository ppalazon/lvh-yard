package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.reachability.InstanceReachability;
import cc.lvhy.yard.dashboard.reachability.Reachability;
import cc.lvhy.yard.dashboard.reachability.RouteReachability;
import cc.lvhy.yard.dashboard.services.AliasTarget;
import cc.lvhy.yard.dashboard.services.Routing;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.SourceKind;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Td;

final class ReachabilityPage {

  private ReachabilityPage() {}

  static String path(Service service) {
    return ServicePage.path(service) + "/reachability";
  }

  static String render(Workstation workstation, Service service, Reachability reachability) {
    return Layout.page(
        "Reachability · " + service.name(),
        workstation.name(),
        main ->
            main.div()
                .attrClass("reachability-page flex flex-col gap-5")
                .div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text("Reachability")
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
                .attrHref(Layout.attr(path(service)))
                .attrClass("btn")
                .text("Test again")
                .__()
                .__()
                .__()
                .p()
                .text(
                    service.name()
                        + " on "
                        + service.workstation()
                        + ", tested from "
                        + workstation.name()
                        + ".")
                .__()
                .div()
                .attrId("reachability")
                .attrClass("flex min-w-0 flex-col gap-4")
                .of(content -> results(content, service, reachability))
                .__()
                .__());
  }

  private static void results(Div<?> content, Service service, Reachability reachability) {
    if (reachability.noRoute() != null) {
      content.p().text(reachability.noRoute()).__();
    }
    if (reachability.routing() != null) {
      Routing routing = reachability.routing();
      content
          .p()
          .of(paragraph -> {
            if (routing.used() != null) {
              paragraph.addAttr("data-routing-source", Layout.attr(routing.used().id()));
            }
          })
          .text(routingNow(service, reachability))
          .__();
    }
    content
        .div()
        .attrClass("min-w-0")
        .table()
        .attrClass("table reachability-table")
        .thead()
        .tr()
        .th()
        .text("Check")
        .__()
        .th()
        .text("Result")
        .__()
        .th()
        .text("Details")
        .__()
        .__()
        .__()
        .tbody()
        .of(body ->
            reachability
                .instances()
                .forEach(instance ->
                    body.tr()
                        .addAttr("data-instance", Layout.attr(instance.id()))
                        .of(row -> {
                          if (reachability.routing() != null && !instance.outOfRange()) {
                            row.addAttr("data-routed", String.valueOf(instance.routed()));
                          }
                        })
                        .td()
                        .text("Instance " + instance.address())
                        .__()
                        .td()
                        .of(cell -> {
                          if (instance.outOfRange()) {
                            cell.span()
                                .attrClass("badge")
                                .addAttr("data-variant", "secondary")
                                .text("Out of range")
                                .__();
                          } else {
                            result(cell, instance.reached());
                          }
                        })
                        .__()
                        .td()
                        .text(instanceDetails(service, reachability, instance))
                        .__()
                        .__()))
        .of(body ->
            reachability
                .routes()
                .forEach(route ->
                    body.tr()
                        .td()
                        .text("Route " + route.scheme() + "://" + route.hostname())
                        .__()
                        .td()
                        .of(cell -> result(cell, route.reached()))
                        .__()
                        .td()
                        .text(routeDetails(route))
                        .__()
                        .__()))
        .__()
        .__()
        .__();
  }

  private static void result(Td<?> cell, boolean reached) {
    cell.span()
        .attrClass("badge")
        .addAttr("data-variant", reached ? "success" : "destructive")
        .text(reached ? "Reached" : "Not reached")
        .__();
  }

  /**
   * Says whether the Route uses it, so a skipped Instance is not taken for a broken one. An
   * Instance of an Alias names its Remote Service.
   */
  private static String instanceDetails(
      Service service, Reachability reachability, InstanceReachability instance) {
    AliasTarget alias = aliasOf(service, instance);
    if (instance.outOfRange()) {
      return "Only "
          + (alias == null ? service.workstation() : alias.workstation())
          + " reaches this address, so it is not tested.";
    }
    String result =
        (instance.reached() ? "Connected directly." : instance.error())
            + (alias == null ? "" : " Given by the Alias to " + alias.label() + ".");
    if (reachability.routing() == null) {
      return result;
    }
    return result + (instance.routed() ? " In the Route." : " Not in the Route now.");
  }

  /** The Remote Service of the Alias that gives {@code instance}, if an Alias gives it. */
  private static AliasTarget aliasOf(Service service, InstanceReachability instance) {
    return service.sources().stream()
        .filter(source -> source.kind() == SourceKind.ALIAS)
        .filter(source ->
            source.givenInstances().stream().anyMatch(given -> given.id().equals(instance.id())))
        .findFirst()
        .map(source -> AliasTarget.parse(source.id()))
        .orElse(null);
  }

  private static String routingNow(Service service, Reachability reachability) {
    Routing routing = reachability.routing();
    if (routing.offWithPassingInstances()) {
      return "The Route uses every passing Instance, as the Instance Priority is off.";
    }
    if (routing.used() == null) {
      return "No Instance passes, so the Route uses every Instance in range.";
    }
    if (routing.kind() == SourceKind.ALIAS) {
      return "The Route sends requests to the Alias's Remote Service"
          + (reachability.alias() == null
              ? "."
              : ": "
                  + reachability.alias().service()
                  + " on "
                  + reachability.alias().workstation()
                  + ".");
    }
    return "The Route uses the passing Instances of "
        + InstanceSourcesCard.label(service, routing.used())
        + ".";
  }

  private static String routeDetails(RouteReachability route) {
    if (!route.reached()) {
      return route.error();
    }
    return route.note() != null ? route.note() : "Answered " + route.status() + " through Traefik.";
  }
}
