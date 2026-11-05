package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.reachability.InstanceReachability;
import dev.palazon.lvhyard.dashboard.reachability.Reachability;
import dev.palazon.lvhyard.dashboard.reachability.RouteReachability;
import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InstanceKind;
import dev.palazon.lvhyard.dashboard.services.Routing;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
import dev.palazon.lvhyard.dashboard.services.ServiceWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

/**
 * A Remote Service's page has no forms or buttons that change it. All but the Reachability test
 * refreshes itself, so a refresh neither runs the test again nor drops its results.
 */
final class ServicePage {

  /** A section card. Sections and tables share it, so they line up. */
  private static final String CARD = "card flex flex-col gap-3 p-4";

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
                                .of(content -> alerts(content, service))
                                .of(content -> summary(content, service))
                                .div()
                                .attrId("sections")
                                .attrClass("grid grid-cols-1 items-start gap-4 lg:grid-cols-2")
                                .of(grid ->
                                    card(grid, null, "Publication")
                                        .of(card -> publication(card, workstation, service, own))
                                        .of(card -> {
                                          if (own) {
                                            publicationForms(card, service);
                                          }
                                        })
                                        .__())
                                .of(grid -> {
                                  if (own && service.published()) {
                                    instancePriority(grid, service);
                                  }
                                })
                                .of(grid -> {
                                  if (own ? service.published() : !service.profiles().isEmpty()) {
                                    routeProfiles(grid, service, own, profileNames);
                                  }
                                })
                                .__()
                                .of(content -> instances(content, service, own))
                                .of(content -> {
                                  if (!service.containerBindings().isEmpty()) {
                                    containerBindings(content, service, own);
                                  }
                                })))
                .div()
                .attrId("reachability")
                .attrClass(CARD)
                .of(card -> reachability(card, workstation, service, reachability))
                .__());
  }

  /** A section card, with its heading written. The caller closes it. */
  private static <Z extends Element> Div<Div<Z>> card(Div<Z> parent, String id, String heading) {
    Div<Div<Z>> card = parent.div();
    if (id != null) {
      card.attrId(id);
    }
    return card.attrClass(CARD).h2().attrClass("text-lg font-semibold").text(heading).__();
  }

  private static void instances(Div<?> content, Service service, boolean own) {
    Div<?> card = card(content, "instances", "Instances");
    if (service.instances().isEmpty()) {
      card.p().text("No Instances are registered.").__().__();
      return;
    }
    card.div()
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
            row.th().of(cell -> cell.span().attrClass("sr-only").text("Actions").__()).__();
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
                        .of(cell -> InstanceViews.description(cell, service, instance))
                        .__()
                        .td()
                        .attrClass("align-top")
                        .of(cell -> InstanceViews.health(cell, instance.health()))
                        .__()
                        .td()
                        .attrClass("align-top")
                        .of(cell -> InstanceViews.checks(cell, instance))
                        .__()
                        .of(row -> {
                          if (own && instance.bound()) {
                            row.td().__();
                          } else if (own) {
                            actions(row, service, instance);
                          }
                        })
                        .__()))
        .__()
        .__()
        .__()
        .__();
  }

  private static void reachability(
      Div<?> content, Workstation workstation, Service service, Reachability reachability) {
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
        .addAttr("data-variant", reached ? "success" : "destructive")
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
    card(content, "container-bindings", "Container Bindings")
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
            boolean logged = service.publication().accessLog();
            buttons
                .form()
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(path(service) + "/publish"))
                .input()
                .attrType(EnumTypeInputType.HIDDEN)
                .attrName("accessLog")
                .attrValue(String.valueOf(!logged))
                .__()
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text(logged ? "Hide from the access log" : "Show in the access log")
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
      card(content, null, "Instance Priority")
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
    card(content, null, "Instance Priority")
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
   * Only this Workstation's Route Profiles can be added, and each name links to its row on the
   * Route Profiles page. A Remote Service shows its names only: they are chosen on its Home
   * Workstation, and its profiles are on that Workstation's page.
   */
  private static void routeProfiles(
      Div<?> content, Service service, boolean own, List<String> profileNames) {
    List<String> chosen = service.profiles();
    List<String> addable = profileNames.stream().filter(name -> !chosen.contains(name)).toList();
    card(content, null, "Route Profiles")
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
                .of(label -> {
                  if (own) {
                    label
                        .text((at + 1) + ". ")
                        .a()
                        .attrHref(Layout.attr(RouteProfilesPage.path(name)))
                        .attrClass("underline")
                        .text(name)
                        .__();
                  } else {
                    label.text((at + 1) + ". " + name);
                  }
                })
                .__()
                .of(buttons -> {
                  if (!own) {
                    return;
                  }
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
          if (!own) {
            return;
          }
          for (String name : addable) {
            List<String> with = Stream.concat(chosen.stream(), Stream.of(name)).toList();
            profilesForm(buttons, service, with, "Add " + name, "Add " + name);
          }
          if (profileNames.isEmpty()) {
            buttons
                .p()
                .attrClass("text-muted-foreground")
                .text("This Workstation has no Route Profiles to add.")
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
   * Red alerts for what stops the hostname from working, then one closed box for the risks. The box
   * has an id so that risks.js can open it again after each refresh.
   */
  private static void alerts(Div<?> content, Service service) {
    Map<ServiceWarning.Severity, List<String>> bySeverity =
        service.warnings().stream()
            .collect(
                Collectors.groupingBy(
                    ServiceWarning::severity,
                    Collectors.mapping(ServiceWarning::message, Collectors.toList())));
    List<String> errors = bySeverity.getOrDefault(ServiceWarning.Severity.ERROR, List.of());
    List<String> risks = bySeverity.getOrDefault(ServiceWarning.Severity.RISK, List.of());
    Stream.concat(stateAlert(service.state()).stream(), errors.stream())
        .forEach(message ->
            content
                .div()
                .attrClass("alert")
                .addAttr("data-variant", "destructive")
                .addAttr("role", "alert")
                .h3()
                .text(message)
                .__()
                .__());
    if (risks.isEmpty()) {
      return;
    }
    // custom: HtmlFlow's details gives its summary no attributes and its section no children.
    content
        .custom("details")
        .attrId("risks")
        .attrClass("alert")
        .addAttr("data-variant", "risk")
        .custom("summary")
        .attrClass("cursor-pointer font-medium")
        .text(
            risks.size() == 1 ? "1 risk to keep in mind" : risks.size() + " risks to keep in mind")
        .__()
        .section()
        .attrClass("mt-2")
        .ul()
        .of(list -> risks.forEach(risk -> list.li().text(risk).__()))
        .__()
        .__()
        .__();
  }

  /**
   * The state is not a Service Warning, so the API does not list it, but it breaks the hostname.
   */
  private static Optional<String> stateAlert(ServiceState state) {
    return switch (state) {
      case UNAVAILABLE -> Optional.of("No Instances are registered");
      case UNHEALTHY -> Optional.of("No Instance passes its checks");
      case UNPUBLISHED, DEGRADED, HEALTHY -> Optional.empty();
    };
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
    div.p()
        .text("Published at ")
        .of(p -> ServiceViews.hostname(p, service))
        .text(", with " + scopeLabel(service) + " Scope" + routedBy)
        .__();
  }

  private static void summary(Div<?> content, Service service) {
    long passing =
        service.instances().stream()
            .filter(instance -> instance.health() == Health.PASSING)
            .count();
    content
        .div()
        .attrClass("grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4")
        .of(grid ->
            summaryCard(grid, "state", "State", card -> ServiceViews.state(card, service.state())))
        .of(grid ->
            summaryCard(
                grid, "publication", "Publication", card -> publicationSummary(card, service)))
        .of(grid ->
            summaryCard(
                grid,
                "passing",
                "Passing Instances",
                card ->
                    card.p()
                        .attrClass("text-2xl font-semibold")
                        .text(passing + " of " + service.instances().size())
                        .__()))
        .of(grid ->
            summaryCard(grid, "routing", "Routes to", card -> routingSummary(card, service)))
        .__();
  }

  private static <Z extends Element> void summaryCard(
      Div<Z> grid, String name, String label, Consumer<Div<Div<Z>>> body) {
    grid.div()
        .attrClass("card flex flex-col items-start gap-1 p-4")
        .addAttr("data-summary", name)
        .p()
        .attrClass("text-sm text-muted-foreground")
        .text(label)
        .__()
        .of(body)
        .__();
  }

  private static void publicationSummary(Div<?> card, Service service) {
    if (!service.published()) {
      card.p().attrClass("font-semibold").text("Not published").__();
      return;
    }
    if (service.hostname() != null) {
      card.p().attrClass("font-semibold").of(p -> ServiceViews.hostname(p, service)).__();
    }
    card.p()
        .text(scopeLabel(service) + " Scope")
        .__()
        .p()
        .attrClass("text-muted-foreground")
        .text("Access log: " + (service.publication().accessLog() ? "on" : "off"))
        .__();
  }

  /** The kind that wins under the Instance Priority, such as Static, with the kinds waiting. */
  private static void routingSummary(Div<?> card, Service service) {
    Routing routing = service.routing();
    if (routing == null) {
      String why;
      if (!service.published()) {
        why = "Not published";
      } else if (service.hostname() == null) {
        why = "No Route on this Workstation";
      } else {
        why = "No Instance in range";
      }
      card.p().attrClass("font-semibold").text(why).__();
      return;
    }
    String routed;
    if (routing.offWithPassingInstances()) {
      routed = "Every passing Instance";
    } else if (routing.kind() == null) {
      routed = "Every Instance, as none is passing";
    } else {
      routed = kindLabel(routing.kind());
    }
    card.p().attrClass("font-semibold").text(routed).__();
    List<String> waiting = service.waitingKinds().stream().map(ServicePage::kindLabel).toList();
    if (routing.off()) {
      card.p().attrClass("text-muted-foreground").text("Instance Priority off").__();
    } else if (!waiting.isEmpty()) {
      card.p()
          .attrClass("text-muted-foreground")
          .text(String.join(", ", waiting) + " waiting")
          .__();
    }
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
