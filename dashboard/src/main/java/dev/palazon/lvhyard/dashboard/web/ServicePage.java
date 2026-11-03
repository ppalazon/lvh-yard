package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.reachability.InstanceReachability;
import dev.palazon.lvhyard.dashboard.reachability.Reachability;
import dev.palazon.lvhyard.dashboard.reachability.RouteReachability;
import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InstanceKind;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceWarning;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

/**
 * A Remote Service's page has no forms or buttons that change it. All but the Reachability test
 * refreshes itself, so a refresh neither runs the test again nor drops its results.
 */
final class ServicePage {

  private ServicePage() {}

  static String path(Service service) {
    return path(service.workstation(), service.name());
  }

  static String path(String workstation, String name) {
    return "/services/" + workstation + "/" + name;
  }

  static String removePath(Service service, Instance instance) {
    return path(service) + "/instances/" + instance.id() + "/remove";
  }

  static String removePath(Service service, ContainerBinding binding) {
    return path(service) + "/container-bindings/" + binding.id() + "/remove";
  }

  static String reachabilityPath(Service service) {
    return path(service) + "/reachability";
  }

  static String publicationPath(Service service) {
    return path(service) + (service.published() ? "/unpublish" : "/publish");
  }

  static String render(Workstation workstation, Service service) {
    return render(workstation, service, null);
  }

  static String render(Workstation workstation, Service service, Reachability reachability) {
    boolean own = workstation.is(service.workstation());
    return Layout.page(
        service.name(),
        workstation.name(),
        main ->
            main.of(page ->
                    Layout.refreshed(
                        page,
                        "service",
                        path(service),
                        region ->
                            region
                                .div()
                                .attrClass("flex flex-wrap items-center gap-3")
                                .h1()
                                .text(service.name())
                                .__()
                                .of(heading -> ServiceViews.state(heading, service.state()))
                                .of(heading -> ServiceViews.core(heading, service))
                                .__()
                                .p()
                                .text(
                                    "On Workstation "
                                        + service.workstation()
                                        + "."
                                        + (own
                                            ? ""
                                            : " It can be changed only on "
                                                + service.workstation()
                                                + "."))
                                .__()
                                .div()
                                .attrClass("flex flex-wrap items-center justify-between gap-4")
                                .of(div -> publication(div, workstation, service, own))
                                .of(div -> {
                                  if (own && !service.core()) {
                                    publicationForms(div, service);
                                  }
                                })
                                .__()
                                .of(content -> {
                                  if (!service.warnings().isEmpty()) {
                                    warnings(content, service);
                                  }
                                })
                                .of(content -> {
                                  if (service.instances().isEmpty()) {
                                    content.p().text("No Instances are registered.").__();
                                  }
                                })
                                .div()
                                .attrClass("table-container")
                                .table()
                                .attrClass("table")
                                .thead()
                                .tr()
                                .th()
                                .text("Instance")
                                .__()
                                .th()
                                .text("Health")
                                .__()
                                .th()
                                .text("Checks")
                                .__()
                                .of(row -> {
                                  if (own) {
                                    row.th()
                                        .of(cell ->
                                            cell.span().attrClass("sr-only").text("Actions").__())
                                        .__();
                                  }
                                })
                                .__()
                                .__()
                                .tbody()
                                .of(body ->
                                    service
                                        .instances()
                                        .forEach(instance ->
                                            body.tr()
                                                .td()
                                                .attrClass("align-top")
                                                .of(cell ->
                                                    InstanceViews.description(
                                                        cell, service, instance))
                                                .__()
                                                .td()
                                                .attrClass("align-top")
                                                .of(cell ->
                                                    InstanceViews.health(cell, instance.health()))
                                                .__()
                                                .td()
                                                .attrClass("align-top")
                                                .of(cell -> InstanceViews.checks(cell, instance))
                                                .__()
                                                .of(row -> {
                                                  if (own
                                                      && (instance.bound() || instance.core())) {
                                                    row.td().__();
                                                  } else if (own) {
                                                    actions(row, service, instance);
                                                  }
                                                })
                                                .__()))
                                .__()
                                .__()
                                .__()
                                .of(content -> {
                                  if (!service.containerBindings().isEmpty()) {
                                    containerBindings(content, service, own);
                                  }
                                })))
                .of(content -> reachability(content, workstation, service, reachability)));
  }

  private static void reachability(
      Main<?> content, Workstation workstation, Service service, Reachability reachability) {
    content
        .div()
        .attrClass("flex flex-wrap items-center justify-between gap-4")
        .h2()
        .attrClass("text-lg font-semibold")
        .text("Reachability from " + workstation.name())
        .__()
        .form()
        .addAttr("method", "get")
        .addAttr("action", Layout.attr(reachabilityPath(service)))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .text(reachability == null ? "Test" : "Test again")
        .__()
        .__()
        .__();
    if (reachability == null) {
      content
          .p()
          .attrClass("text-muted-foreground")
          .text("Tests whether this Workstation reaches the Service.")
          .__();
      return;
    }
    if (reachability.noRoute() != null) {
      content.p().text(reachability.noRoute()).__();
    }
    content
        .div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
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
                        .text(instanceDetails(service, instance))
                        .__()
                        .__()))
        .of(body ->
            reachability
                .routes()
                .forEach(route ->
                    body.tr()
                        .td()
                        .text("Route " + route.hostname())
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
        .addAttr("data-variant", reached ? "primary" : "destructive")
        .text(reached ? "Reached" : "Not reached")
        .__();
  }

  private static String instanceDetails(Service service, InstanceReachability instance) {
    if (instance.outOfRange()) {
      return "Only " + service.workstation() + " reaches this address, so it is not tested.";
    }
    return instance.reached() ? "Connected directly." : instance.error();
  }

  private static String routeDetails(RouteReachability route) {
    return route.reached() ? "Answered " + route.status() + " through Traefik." : route.error();
  }

  private static void containerBindings(Div<?> content, Service service, boolean own) {
    content
        .h2()
        .attrClass("text-lg font-semibold")
        .text("Container Bindings")
        .__()
        .div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .th()
        .text("Follows")
        .__()
        .th()
        .text("Container port")
        .__()
        .th()
        .text("Bound Instances")
        .__()
        .of(row -> {
          if (own) {
            row.th().of(cell -> cell.span().attrClass("sr-only").text("Actions").__()).__();
          }
        })
        .__()
        .__()
        .tbody()
        .of(body ->
            service
                .containerBindings()
                .forEach(binding ->
                    body.tr()
                        .td()
                        .text(binding.follows())
                        .__()
                        .td()
                        .text(String.valueOf(binding.port()))
                        .__()
                        .td()
                        .of(cell -> {
                          if (binding.instances().isEmpty()) {
                            cell.span()
                                .attrClass("text-muted-foreground")
                                .text(
                                    binding.container() != null
                                        ? "No container of this name runs."
                                        : "No container of this Compose service runs.")
                                .__();
                          }
                          service.instances().stream()
                              .filter(instance -> binding.instances().contains(instance.id()))
                              .forEach(instance -> cell.div().text(instance.address()).__());
                        })
                        .__()
                        .of(row -> {
                          if (own) {
                            row.td()
                                .div()
                                .attrClass("flex justify-end")
                                .form()
                                .addAttr("method", "post")
                                .addAttr("action", Layout.attr(removePath(service, binding)))
                                .button()
                                .attrType(EnumTypeButtonType.SUBMIT)
                                .attrClass("btn")
                                .addAttr("data-variant", "outline")
                                .addAttr("data-size", "sm")
                                .text("Remove")
                                .__()
                                .__()
                                .__()
                                .__();
                          }
                        })
                        .__()))
        .__()
        .__()
        .__();
  }

  private static void publicationForms(Div<?> div, Service service) {
    div.div()
        .attrClass("flex flex-wrap gap-2")
        .of(buttons -> {
          if (service.published()) {
            boolean yard = service.publication().scope() == Scope.YARD;
            buttons
                .form()
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(path(service) + "/publish"))
                .input()
                .attrType(EnumTypeInputType.HIDDEN)
                .attrName("scope")
                .attrValue(yard ? Scope.LOCAL.value() : Scope.YARD.value())
                .__()
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text(yard ? "Keep on this Workstation" : "Share with the Yard")
                .__()
                .__();
          }
        })
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(publicationPath(service)))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .of(button -> {
          if (service.published()) {
            button.addAttr("data-variant", "outline");
          }
        })
        .text(service.published() ? "Unpublish" : "Publish")
        .__()
        .__()
        .__();
  }

  /** Why other Workstations may not reach the Service, though its Scope gives them a Route. */
  private static void warnings(Div<?> content, Service service) {
    content
        .div()
        .attrClass("alert")
        .addAttr("data-variant", "destructive")
        .addAttr("role", "alert")
        .h3()
        .text("Other Workstations may not reach this Service")
        .__()
        .section()
        .ul()
        .of(list ->
            service.warnings().stream()
                .map(ServiceWarning::message)
                .forEach(message -> list.li().text(message).__()))
        .__()
        .__()
        .__();
  }

  private static void actions(Tr<?> row, Service service, Instance instance) {
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex justify-end gap-2")
        .a()
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .attrHref(Layout.attr(EditInstancePage.path(service, instance)))
        .text("Edit")
        .__()
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(removePath(service, instance)))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .text("Remove")
        .__()
        .__()
        .__()
        .__();
  }

  /** A Remote Service with Local Scope has no hostname here. */
  private static void publication(
      Div<?> div, Workstation workstation, Service service, boolean own) {
    if (!service.published()) {
      div.p().text("Not published.").__();
      return;
    }
    boolean yard = service.publication().scope() == Scope.YARD;
    if (!own && !yard) {
      div.p()
          .text(
              "Published with "
                  + scopeLabel(service)
                  + " Scope: only "
                  + service.workstation()
                  + " routes it.")
          .__();
      return;
    }
    String routedBy;
    if (!yard) {
      routedBy = ".";
    } else if (own) {
      routedBy =
          ". The other Workstations route it at " + workstation.yardHostname(service.name()) + ".";
    } else {
      routedBy = ".";
    }
    div.div()
        .p()
        .text("Published at ")
        .of(p -> ServiceViews.hostname(p, service))
        .text(", with " + scopeLabel(service) + " Scope" + routedBy)
        .__()
        .of(texts -> {
          if (service.routing() != null) {
            texts.p().attrClass("text-muted-foreground").text(routing(service)).__();
          }
        })
        .__();
  }

  /** Such as {@code Routing to: Static (Bound waiting)}. */
  private static String routing(Service service) {
    if (service.routing().kind() == null) {
      return "Routing to: every Instance, as none is passing.";
    }
    List<String> waiting = service.waitingKinds().stream().map(ServicePage::kindLabel).toList();
    return "Routing to: "
        + kindLabel(service.routing().kind())
        + (waiting.isEmpty() ? "" : " (" + String.join(", ", waiting) + " waiting)");
  }

  private static String kindLabel(InstanceKind kind) {
    return switch (kind) {
      case STATIC -> "Static";
      case BOUND -> "Bound";
      case ALIAS -> "Alias";
    };
  }

  private static String scopeLabel(Service service) {
    return switch (service.publication().scope()) {
      case LOCAL -> "Local";
      case YARD -> "Yard";
    };
  }
}
