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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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

  /**
   * @param profileNames this Workstation's valid Route Profiles, which its own Services can name
   * @param reachability the results of a Reachability test, or {@code null}
   */
  static String render(
      Workstation workstation,
      Service service,
      List<String> profileNames,
      Reachability reachability) {
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
                                  if (own && !service.core() && service.published()) {
                                    instancePriority(content, service);
                                  }
                                })
                                .of(content -> {
                                  if (own && service.published()) {
                                    routeProfiles(content, service, profileNames);
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
    if (reachability.routing() != null) {
      content.p().text(routingNow(reachability)).__();
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
                        .text(instanceDetails(service, reachability, instance))
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

  /** Says whether the Route uses it, so a skipped Instance is not taken for a broken one. */
  private static String instanceDetails(
      Service service, Reachability reachability, InstanceReachability instance) {
    if (instance.outOfRange()) {
      return "Only " + service.workstation() + " reaches this address, so it is not tested.";
    }
    String result = instance.reached() ? "Connected directly." : instance.error();
    if (reachability.routing() == null) {
      return result;
    }
    return result + (instance.routed() ? " In the Route." : " Not in the Route now.");
  }

  private static String routingNow(Reachability reachability) {
    InstanceKind kind = reachability.routing().kind();
    if (reachability.routing().offWithPassingInstances()) {
      return "The Route uses every passing Instance, as the Instance Priority is off.";
    }
    if (kind == null) {
      return "No Instance passes, so the Route uses every Instance in range.";
    }
    if (kind == InstanceKind.ALIAS) {
      return "The Route sends requests to the Alias's Remote Service"
          + (reachability.alias() == null
              ? "."
              : ": "
                  + reachability.alias().service()
                  + " on "
                  + reachability.alias().workstation()
                  + ".");
    }
    return "The Route uses the passing " + kindLabel(kind) + " Instances now.";
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

  /**
   * Each button posts the whole new order, so the page needs no script. The last kind has no Remove
   * button, as the empty order means off, which has its own button.
   */
  private static void instancePriority(Div<?> content, Service service) {
    List<InstanceKind> order = service.priority();
    if (order.isEmpty()) {
      content
          .div()
          .attrClass("flex flex-col gap-2")
          .h2()
          .attrClass("text-lg font-semibold")
          .text("Instance Priority")
          .__()
          .p()
          .attrClass("text-muted-foreground")
          .text("Off: the passing Static and Bound Instances share the Route.")
          .__()
          .div()
          .attrClass("flex flex-wrap gap-2")
          .of(buttons ->
              priorityForm(
                  buttons, service, InstanceKind.DEFAULT_PRIORITY, "Use an order", "Use an order"))
          .__()
          .__();
      return;
    }
    content
        .div()
        .attrClass("flex flex-col gap-2")
        .h2()
        .attrClass("text-lg font-semibold")
        .text("Instance Priority")
        .__()
        .p()
        .attrClass("text-muted-foreground")
        .text("The Route uses the first kind that has a passing Instance.")
        .__()
        .ol()
        .attrClass("flex flex-col gap-2")
        .of(list -> {
          for (int i = 0; i < order.size(); i++) {
            InstanceKind kind = order.get(i);
            String label = kindLabel(kind);
            int at = i;
            list.li()
                .div()
                .attrClass("flex flex-wrap items-center gap-2")
                .span()
                .attrClass("min-w-16")
                .text((at + 1) + ". " + label)
                .__()
                .of(buttons -> {
                  if (at > 0) {
                    priorityForm(
                        buttons, service, moved(order, at, at - 1), "Up", "Move " + label + " up");
                  }
                  if (at < order.size() - 1) {
                    priorityForm(
                        buttons,
                        service,
                        moved(order, at, at + 1),
                        "Down",
                        "Move " + label + " down");
                  }
                  if (order.size() > 1) {
                    List<InstanceKind> without =
                        order.stream().filter(other -> other != kind).toList();
                    priorityForm(buttons, service, without, "Remove", "Remove " + label);
                  }
                })
                .__()
                .__();
          }
        })
        .__()
        .div()
        .attrClass("flex flex-wrap gap-2")
        .of(buttons -> {
          for (InstanceKind kind : InstanceKind.values()) {
            if (!order.contains(kind)) {
              List<InstanceKind> with = Stream.concat(order.stream(), Stream.of(kind)).toList();
              String add = "Add " + kindLabel(kind);
              priorityForm(buttons, service, with, add, add);
            }
          }
          priorityForm(buttons, service, List.of(), "Turn off", "Turn off");
        })
        .__()
        .__();
  }

  /** {@code label} is the button's accessible name, as several buttons share a short text. */
  private static void priorityForm(
      Div<?> buttons, Service service, List<InstanceKind> order, String text, String label) {
    buttons
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(path(service) + "/publish"))
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("priority")
        .attrValue(String.join(",", order.stream().map(InstanceKind::value).toList()))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr(label))
        .text(text)
        .__()
        .__();
  }

  private static <T> List<T> moved(List<T> order, int from, int to) {
    List<T> moved = new ArrayList<>(order);
    moved.add(to, moved.remove(from));
    return moved;
  }

  /**
   * Like the Instance Priority, each button posts the whole new list, so the page needs no script.
   * Only names from this Workstation's file can be added.
   */
  private static void routeProfiles(Div<?> content, Service service, List<String> profileNames) {
    List<String> chosen = service.profiles();
    List<String> addable = profileNames.stream().filter(name -> !chosen.contains(name)).toList();
    content
        .div()
        .attrClass("flex flex-col gap-2")
        .h2()
        .attrClass("text-lg font-semibold")
        .text("Route Profiles")
        .__()
        .p()
        .attrClass("text-muted-foreground")
        .text(
            chosen.isEmpty()
                ? "None: the Route sends requests as they come."
                : "The Route applies them in this order.")
        .__()
        .ol()
        .attrClass("flex flex-col gap-2")
        .of(list -> {
          for (int i = 0; i < chosen.size(); i++) {
            String name = chosen.get(i);
            int at = i;
            list.li()
                .div()
                .attrClass("flex flex-wrap items-center gap-2")
                .span()
                .attrClass("min-w-16")
                .text((at + 1) + ". " + name)
                .__()
                .of(buttons -> {
                  if (at > 0) {
                    profilesForm(
                        buttons, service, moved(chosen, at, at - 1), "Up", "Move " + name + " up");
                  }
                  if (at < chosen.size() - 1) {
                    profilesForm(
                        buttons,
                        service,
                        moved(chosen, at, at + 1),
                        "Down",
                        "Move " + name + " down");
                  }
                  List<String> without =
                      chosen.stream().filter(other -> !other.equals(name)).toList();
                  profilesForm(buttons, service, without, "Remove", "Remove " + name);
                })
                .__()
                .__();
          }
        })
        .__()
        .div()
        .attrClass("flex flex-wrap gap-2")
        .of(buttons -> {
          for (String name : addable) {
            List<String> with = Stream.concat(chosen.stream(), Stream.of(name)).toList();
            profilesForm(buttons, service, with, "Add " + name, "Add " + name);
          }
          if (profileNames.isEmpty()) {
            buttons
                .p()
                .attrClass("text-muted-foreground")
                .text("route-profiles.yaml has no valid profiles to add.")
                .__();
          }
        })
        .__()
        .__();
  }

  /** {@code label} is the button's accessible name, as several buttons share a short text. */
  private static void profilesForm(
      Div<?> buttons, Service service, List<String> profiles, String text, String label) {
    buttons
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(path(service) + "/publish"))
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("profiles")
        .attrValue(Layout.attr(String.join(",", profiles)))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr(label))
        .text(text)
        .__()
        .__();
  }

  /**
   * Why other Workstations may not reach the Service, though its Scope gives them a Route, or why a
   * Route Profile does not protect it everywhere.
   */
  private static void warnings(Div<?> content, Service service) {
    content
        .div()
        .attrClass("alert")
        .addAttr("data-variant", "destructive")
        .addAttr("role", "alert")
        .h3()
        .text("Check this Service")
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
    if (service.routing().offWithPassingInstances()) {
      return "Routing to: every passing Instance (Instance Priority off)";
    }
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
