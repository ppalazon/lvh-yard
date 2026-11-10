package dev.palazon.lvhyard.dashboard.web;

import static dev.palazon.lvhyard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
import dev.palazon.lvhyard.dashboard.services.ServiceWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;

/** A Remote Service's page shows its setup without controls that change it. */
final class ServicePage {

  /** A section card. Sections and tables share it, so they line up. */
  static final String CARD = "card flex flex-col gap-3 p-4";

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

  static String instancePriorityPath(Service service) {
    return path(service) + "/instance-priority";
  }

  static String publicationPath(Service service) {
    return path(service) + (service.published() ? "/unpublish" : "/publish");
  }

  /**
   * @param aliases the Aliases of an own Service, with their state, in order
   * @param profileNames this Workstation's valid Route Profiles, which its own Services can name
   */
  static String render(
      Workstation workstation, Service service, List<Alias> aliases, List<String> profileNames) {
    boolean own = workstation.is(service.workstation());
    return Layout.page(
        service.name(),
        workstation.name(),
        main ->
            main.div()
                .attrClass("service-page flex flex-col gap-5")
                .of(page ->
                    Layout.refreshed(
                        page,
                        "service",
                        path(service),
                        YARD_CHANGED,
                        region ->
                            region
                                .of(content -> header(content, service, own))
                                .of(content -> foreignOnly(content, service))
                                .of(content -> alerts(content, service))
                                .div()
                                .attrId("sections")
                                .attrClass("grid grid-cols-1 items-start gap-4 lg:grid-cols-2")
                                .of(grid ->
                                    card(grid, "publication", "Publication")
                                        .of(card -> publication(card, workstation, service, own))
                                        .of(card -> {
                                          if (own) {
                                            publicationForms(card, service);
                                          }
                                        })
                                        .__())
                                .of(grid -> {
                                  if (own || !service.profiles().isEmpty()) {
                                    routeProfiles(grid, service, own, profileNames);
                                  }
                                })
                                .of(grid -> InstanceSourcesCard.render(grid, service, aliases, own))
                                .__()))
                .of(content -> {
                  if (own) {
                    content
                        .div()
                        .attrId("delete")
                        .a()
                        .attrHref(Layout.attr(DeleteServicePage.path(service)))
                        .attrClass("btn w-fit")
                        .addAttr("data-variant", "destructive")
                        .text("Delete Service")
                        .__()
                        .__();
                  }
                })
                .__());
  }

  /**
   * Why a Service with no record is here, and why a delete leaves it. The first change that needs a
   * record writes one, and the note goes.
   */
  private static void foreignOnly(Div<?> content, Service service) {
    if (service.created()) {
      return;
    }
    content
        .p()
        .addAttr("data-foreign-only", "")
        .text(
            "Only Foreign Instances give this Service, so it stays while they are registered,"
                + " also after a delete.")
        .__();
  }

  /** A section card, with its heading written. The caller closes it. */
  private static <Z extends Element> Div<Div<Z>> card(Div<Z> parent, String id, String heading) {
    Div<Div<Z>> card = parent.div();
    if (id != null) {
      card.attrId(id);
    }
    return card.attrClass(CARD).h2().attrClass("text-lg font-semibold").text(heading).__();
  }

  static <T> List<T> moved(List<T> order, int from, int to) {
    List<T> moved = new ArrayList<>(order);
    moved.add(to, moved.remove(from));
    return moved;
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
   * Like the Instance Sources, each button posts the whole new list, so the page needs no script.
   * Only this Workstation's Route Profiles can be added, and each name links to its row on the
   * Route Profiles page. On an Unpublished Service, adding one publishes it. A Remote Service shows
   * its names only: they are chosen on its Home Workstation, and its profiles are on that
   * Workstation's page.
   */
  private static void routeProfiles(
      Div<?> content, Service service, boolean own, List<String> profileNames) {
    List<String> chosen = service.profiles();
    List<String> addable = profileNames.stream().filter(name -> !chosen.contains(name)).toList();
    card(content, "route-profiles", "Route Profiles")
        .p()
        .attrClass("text-muted-foreground")
        .text(
            !service.published()
                ? "Not published. Adding one publishes the Service with Local Scope."
                : chosen.isEmpty()
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
   * has a key so that disclosures.js can open it again after each refresh.
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
        .addAttr("data-preserve-open", "risks")
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

  private static void publication(
      Div<?> div, Workstation workstation, Service service, boolean own) {
    if (!service.published()) {
      div.p().text("Not published.").__();
      return;
    }
    boolean yard = service.publication().scope() == Scope.YARD;
    div.p()
        .text(
            scopeLabel(service)
                + " Scope: "
                + (yard
                    ? "every Workstation routes it."
                    : "only " + service.workstation() + " routes it."))
        .__();
    if (own && yard) {
      div.p().text("Other Workstations use " + workstation.yardHostname(service.name()) + ".").__();
    }
    div.p()
        .attrClass("text-sm text-muted-foreground")
        .text("Access log: " + (service.publication().accessLog() ? "on" : "off"))
        .__();
  }

  private static void header(Div<?> content, Service service, boolean own) {
    long passing =
        service.instances().stream()
            .filter(instance -> instance.health() == Health.PASSING)
            .count();
    content
        .div()
        .attrClass("service-heading space-y-2")
        .div()
        .attrClass("flex flex-wrap items-center gap-3")
        .h1()
        .text(service.name())
        .__()
        .of(heading -> ServiceViews.state(heading, service.state()))
        .a()
        .attrHref(Layout.attr(ReachabilityPage.path(service)))
        .attrClass("btn ml-auto")
        .addAttr("data-variant", "outline")
        .text("Test Reachability")
        .__()
        .__()
        .p()
        .attrClass("text-lg")
        .of(p -> {
          if (service.hostname() != null) {
            ServiceViews.hostname(p, service);
          } else {
            p.text("No hostname on this Workstation");
          }
        })
        .__()
        .p()
        .attrClass("font-medium")
        .text(passing + " of " + service.instances().size() + " Instances passing")
        .__()
        .p()
        .attrClass("text-sm text-muted-foreground")
        .text(
            "On Workstation "
                + service.workstation()
                + "."
                + (own ? "" : " It can be changed only on " + service.workstation() + "."))
        .__()
        .__();
  }

  private static String scopeLabel(Service service) {
    return switch (service.publication().scope()) {
      case LOCAL -> "Local";
      case YARD -> "Yard";
    };
  }
}
