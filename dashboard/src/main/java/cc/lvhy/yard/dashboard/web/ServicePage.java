package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.reachability.TraefikSettings;
import cc.lvhy.yard.dashboard.services.Alias;
import cc.lvhy.yard.dashboard.services.ContainerBinding;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.ServiceState;
import cc.lvhy.yard.dashboard.services.ServiceWarning;
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
      Workstation workstation,
      Service service,
      List<Alias> aliases,
      List<String> profileNames,
      TraefikSettings traefik) {
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
                                .of(content -> header(content, service, own, traefik))
                                .of(content -> foreignOnly(content, service))
                                .div()
                                .attrId("sections")
                                .attrClass("grid grid-cols-1 items-start gap-4 lg:grid-cols-2")
                                .of(grid ->
                                    card(grid, "publication", "Publication")
                                        .of(card -> {
                                          if (own) {
                                            publicationSwitches(card, workstation, service);
                                          } else {
                                            publication(card, service);
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

  /**
   * Each switch posts its own form, so one flip changes one setting. Each has a line that says what
   * its setting does now.
   */
  private static void publicationSwitches(Div<?> div, Workstation workstation, Service service) {
    div.div()
        .attrClass("flex flex-col gap-4")
        .of(switches -> {
          SwitchForm.render(
              switches,
              publicationPath(service),
              null,
              null,
              service.published(),
              "Published",
              service.published()
                  ? "The hostname routes requests to its Instances."
                  : "Not published, so it has no hostname.");
          if (service.published()) {
            boolean yard = service.publication().scope() == Scope.YARD;
            SwitchForm.render(
                switches,
                path(service) + "/publish",
                "scope",
                yard ? Scope.LOCAL.value() : Scope.YARD.value(),
                yard,
                "Share with the Yard",
                yard
                    ? "Yard Scope: every Workstation routes it. Other Workstations use "
                        + workstation.yardHostname(service.name())
                        + "."
                    : "Local Scope: only " + service.workstation() + " routes it.");
            boolean logged = service.publication().accessLog();
            SwitchForm.render(
                switches,
                path(service) + "/publish",
                "accessLog",
                String.valueOf(!logged),
                logged,
                "Show in the access log",
                logged
                    ? "Traefik logs each request to the Route. To see the log, run this command:"
                    : "Traefik does not log requests to the Route.",
                logged ? "lvh-yard ws compose logs traefik" : null);
          }
        })
        .__();
  }

  /**
   * Like the Instance Sources, each button posts the whole new list, so the page needs no script.
   * Only this Workstation's Route Profiles can be added, in the selector at the right of the title,
   * and each name links to its row on the Route Profiles page. On an Unpublished Service, adding
   * one publishes it. A Remote Service shows its names only: they are chosen on its Home
   * Workstation, and its profiles are on that Workstation's page.
   */
  private static void routeProfiles(
      Div<?> content, Service service, boolean own, List<String> profileNames) {
    List<String> chosen = service.profiles();
    List<String> addable = profileNames.stream().filter(name -> !chosen.contains(name)).toList();
    content
        .div()
        .attrId("route-profiles")
        .attrClass(CARD)
        .div()
        .attrClass("flex flex-wrap items-center justify-between gap-3")
        .h2()
        .attrClass("text-lg font-semibold")
        .text("Route Profiles")
        .__()
        .of(heading -> {
          if (own && !addable.isEmpty()) {
            addProfileForm(heading, service, chosen, addable);
          }
        })
        .__()
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
        .attrClass("flex flex-col gap-1")
        .of(list -> {
          for (int i = 0; i < chosen.size(); i++) {
            String name = chosen.get(i);
            int at = i;
            list.li()
                .div()
                .attrClass(
                    own
                        ? "-mx-2 flex items-center justify-between gap-2 rounded-md px-2 py-1"
                            + " hover:bg-muted focus-within:bg-muted"
                        : "flex items-center justify-between gap-2")
                .span()
                .attrClass("min-w-0")
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
                .of(row -> {
                  if (own) {
                    profileActions(row, service, chosen, at, name);
                  }
                })
                .__()
                .__();
          }
        })
        .__()
        .of(card -> {
          if (own && addable.isEmpty()) {
            card.p()
                .attrClass("text-muted-foreground")
                .of(message -> {
                  if (profileNames.isEmpty()) {
                    message
                        .text("This Workstation has no Route Profiles to add. Create one on the ")
                        .a()
                        .attrHref(RouteProfilesPage.PATH)
                        .attrClass("underline")
                        .text("Route Profiles page")
                        .__()
                        .text(".");
                  } else {
                    message.text("Every Route Profile of this Workstation is in use here.");
                  }
                })
                .__();
          }
        })
        .__();
  }

  /**
   * One small icon button in its own form. {@code label} is its accessible name, as several buttons
   * share an icon. {@code variant} is the Basecoat variant of the button.
   */
  private static void profilesForm(
      Div<?> actions,
      Service service,
      List<String> profiles,
      String label,
      String variant,
      String... iconPaths) {
    actions
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
        .addAttr("data-variant", variant)
        .addAttr("data-size", "icon-sm")
        .addAttr("aria-label", Layout.attr(label))
        .addAttr("title", Layout.attr(label))
        .of(button -> Layout.icon(button.svg(), "size-4", iconPaths))
        .__()
        .__();
  }

  /**
   * Up, Down and Remove sit in three fixed slots at the right of the row, so each is in the same
   * place in every row. The first profile has no Up and the last has no Down. Their slots stay
   * empty.
   */
  private static void profileActions(
      Div<?> row, Service service, List<String> chosen, int at, String name) {
    row.div()
        .attrClass("grid shrink-0 grid-cols-[repeat(3,2rem)] gap-1")
        .of(actions -> {
          if (at > 0) {
            profilesForm(
                actions,
                service,
                moved(chosen, at, at - 1),
                "Move " + name + " up",
                "outline",
                "m5 12 7-7 7 7",
                "M12 19V5");
          } else {
            actions.span().addAttr("aria-hidden", "true").__();
          }
          if (at < chosen.size() - 1) {
            profilesForm(
                actions,
                service,
                moved(chosen, at, at + 1),
                "Move " + name + " down",
                "outline",
                "M12 5v14",
                "m19 12-7 7-7-7");
          } else {
            actions.span().addAttr("aria-hidden", "true").__();
          }
          List<String> without = chosen.stream().filter(other -> !other.equals(name)).toList();
          profilesForm(
              actions,
              service,
              without,
              "Remove " + name,
              "destructive",
              "M18 6 6 18",
              "m6 6 12 12");
        })
        .__();
  }

  /**
   * The Route Profiles that the Service does not use are in one selector. Each option holds the
   * whole new list, so the form needs no script.
   */
  private static void addProfileForm(
      Div<?> row, Service service, List<String> chosen, List<String> addable) {
    row.form()
        .attrClass("flex flex-wrap items-center gap-2")
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(path(service) + "/publish"))
        .select()
        .attrClass("select w-auto min-w-48")
        .attrName("profiles")
        .addAttr("aria-label", "Route Profile to add")
        .of(select ->
            addable.forEach(name ->
                select
                    .option()
                    .attrValue(
                        Layout.attr(
                            String.join(
                                ",", Stream.concat(chosen.stream(), Stream.of(name)).toList())))
                    .text(name)
                    .__()))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .text("Add")
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

  /** A Remote Service shows its Publication as text. Its Home Workstation has the switches. */
  private static void publication(Div<?> div, Service service) {
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
        .__()
        .p()
        .attrClass("text-sm text-muted-foreground")
        .text("Access log: " + (service.publication().accessLog() ? "on" : "off"))
        .__();
  }

  /**
   * The name, state and Test Reachability, then two columns: the hostname, the passing count and
   * the Home Workstation on the left, the alerts on the right. They stack on a narrow screen.
   */
  private static void header(
      Div<?> content, Service service, boolean own, TraefikSettings traefik) {
    long passing =
        service.instances().stream()
            .filter(instance -> instance.health() == Health.PASSING)
            .count();
    content
        .div()
        .attrClass("service-heading space-y-3")
        .div()
        .attrClass("flex flex-wrap items-center gap-3")
        .h1()
        .text(service.name())
        .__()
        .of(heading -> ServiceViews.state(heading, service.state()))
        .a()
        .attrHref(Layout.attr(ReachabilityPage.path(service)))
        .attrClass("btn ml-auto")
        .text("Test Reachability")
        .__()
        .__()
        .div()
        .attrClass("grid items-start gap-4 lg:grid-cols-2")
        .div()
        .attrClass("min-w-0 space-y-2")
        .p()
        .attrClass("text-lg")
        .of(p -> {
          if (service.hostname() != null) {
            ServiceViews.hostname(p, service, traefik);
          } else {
            p.text("No hostname on this Workstation");
          }
        })
        .__()
        .p()
        .attrClass("font-medium")
        .text(passing + " of " + service.instances().size() + " Instances passing")
        .__()
        .of(facts -> {
          // An Unpublished Service has no Route, and its state says so already.
          if (service.published()) {
            InstanceSourcesCard.routingSummary(facts, service);
          }
        })
        .p()
        .attrClass("text-sm text-muted-foreground")
        .text(
            "On Workstation "
                + service.workstation()
                + "."
                + (own ? "" : " It can be changed only on " + service.workstation() + "."))
        .__()
        .__()
        .div()
        .attrClass("flex min-w-0 flex-col gap-2 empty:hidden")
        .of(column -> alerts(column, service))
        .__()
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
