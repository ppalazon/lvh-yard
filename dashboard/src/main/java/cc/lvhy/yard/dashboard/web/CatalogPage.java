package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.HomeWorkstation;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.ServiceState;
import cc.lvhy.yard.dashboard.services.Yard;
import cc.lvhy.yard.dashboard.services.YardWorkstation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.web.util.UriComponentsBuilder;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Tbody;

/** The Services refresh themselves, with the filters. */
final class CatalogPage {

  private CatalogPage() {}

  /**
   * A {@code null} filter does not filter. All stay in the page's address, as {@code
   * ?workstation=<name>&state=<state>&scope=<scope>}. A Service that is not published has no Scope,
   * so a Scope filter hides it.
   */
  record Filters(String workstation, ServiceState state, Scope scope) {

    boolean shows(String name) {
      return workstation == null || workstation.equals(name);
    }

    List<Service> of(List<Service> services) {
      return services.stream()
          .filter(service -> state == null || service.state() == state)
          .filter(service -> scope == null || scopeOf(service) == scope)
          .toList();
    }

    private static Scope scopeOf(Service service) {
      return service.published() ? service.publication().scope() : null;
    }

    String path() {
      return UriComponentsBuilder.fromPath("/")
          .queryParamIfPresent("workstation", Optional.ofNullable(workstation))
          .queryParamIfPresent("state", Optional.ofNullable(state).map(ServiceState::value))
          .queryParamIfPresent("scope", Optional.ofNullable(scope).map(Scope::value))
          .encode()
          .build()
          .toUriString();
    }
  }

  static String render(Workstation workstation, Yard yard, Filters filters) {
    return Layout.page(
        "Catalog",
        workstation.name(),
        Layout.Section.CATALOG,
        main ->
            main.div()
                .attrClass("catalog-page flex flex-col gap-4")
                .div()
                .attrClass("grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1")
                .h1()
                .text("Catalog")
                .__()
                .of(content -> FormPage.openLink(content, NewServicePage.SPEC))
                .p()
                .attrClass("col-span-2 text-sm text-muted-foreground")
                .text(
                    "Workstation "
                        + workstation.name()
                        + ", base domain "
                        + workstation.baseDomain()
                        + ".")
                .__()
                .__()
                .of(content -> filterForm(content, yard, filters))
                .of(content ->
                    Layout.refreshed(
                        content,
                        "catalog",
                        filters.path(),
                        YARD_CHANGED,
                        region -> workstations(region, yard, filters)))
                .__());
  }

  private static void workstations(Div<?> region, Yard yard, Filters filters) {
    if (filters.shows(yard.home().name())) {
      home(region, yard.home(), filters);
    }
    List<YardWorkstation> remotes =
        yard.remotes().stream().filter(remote -> filters.shows(remote.name())).toList();
    remotes.forEach(remote -> remote(region, remote, filters));
    if (!filters.shows(yard.home().name()) && remotes.isEmpty()) {
      region.p().text("The Yard has no Workstation " + filters.workstation() + ".").__();
    }
  }

  /** After a change, the swapped catalog region refreshes with the new filters. */
  private static void filterForm(Div<?> main, Yard yard, Filters filters) {
    List<String> names =
        Stream.concat(
                Stream.of(yard.home().name()), yard.remotes().stream().map(YardWorkstation::name))
            .toList();
    main.form()
        .attrClass("catalog-filters flex flex-wrap items-end gap-3")
        .addAttr("hx-get", "/")
        .addAttr("hx-trigger", "change")
        .addAttr("hx-target", "#catalog")
        .addAttr("hx-select", "#catalog")
        .addAttr("hx-swap", "outerHTML")
        .addAttr("hx-push-url", "true")
        .addAttr("hx-status:4xx", "swap:none")
        .addAttr("hx-status:5xx", "swap:none")
        .div()
        .attrClass("flex min-w-0 flex-col gap-1.5 md:flex-row md:items-center md:gap-3")
        .label()
        .attrFor("workstation")
        .text("Workstation")
        .__()
        .select()
        .attrClass("select")
        .attrId("workstation")
        .attrName("workstation")
        .option()
        .attrValue("")
        .text("Every Workstation")
        .__()
        .of(select ->
            names.forEach(name ->
                select
                    .option()
                    .attrValue(Layout.attr(name))
                    .of(option -> option.attrSelected(name.equals(filters.workstation())))
                    .text(name)
                    .__()))
        .__()
        .__()
        .div()
        .attrClass("flex min-w-0 flex-col gap-1.5 md:flex-row md:items-center md:gap-3")
        .label()
        .attrFor("state")
        .text("State")
        .__()
        .select()
        .attrClass("select")
        .attrId("state")
        .attrName("state")
        .option()
        .attrValue("")
        .text("Every state")
        .__()
        .of(select ->
            Arrays.stream(ServiceState.values())
                .forEach(state ->
                    select
                        .option()
                        .attrValue(state.value())
                        .of(option -> option.attrSelected(state == filters.state()))
                        .text(ServiceViews.label(state))
                        .__()))
        .__()
        .__()
        .div()
        .attrClass("flex min-w-0 flex-col gap-1.5 md:flex-row md:items-center md:gap-3")
        .label()
        .attrFor("scope")
        .text("Scope")
        .__()
        .select()
        .attrClass("select")
        .attrId("scope")
        .attrName("scope")
        .option()
        .attrValue("")
        .text("Every scope")
        .__()
        .of(select ->
            Arrays.stream(Scope.values())
                .forEach(scope ->
                    select
                        .option()
                        .attrValue(scope.value())
                        .of(option -> option.attrSelected(scope == filters.scope()))
                        .text(scopeLabel(scope))
                        .__()))
        .__()
        .__()
        .__();
  }

  private static void home(Div<?> region, HomeWorkstation home, Filters filters) {
    region
        .div()
        .attrClass("catalog-workstation space-y-2")
        .of(group -> {
          heading(group, home.name(), "secondary", "This Workstation");
          services(group, home.services(), filters, false);
        })
        .__();
  }

  private static void remote(Div<?> region, YardWorkstation remote, Filters filters) {
    region
        .div()
        .attrClass("catalog-workstation space-y-2")
        .of(group -> {
          if (remote.unreachable()) {
            heading(group, remote.name(), "destructive", "Workstation unreachable");
            group
                .p()
                .text("Its Consul does not answer. Its Services show again when it does.")
                .__();
          } else {
            heading(group, remote.name(), null, null);
            services(group, remote.services(), filters, true);
          }
        })
        .__();
  }

  private static void heading(Div<?> region, String name, String variant, String badge) {
    region
        .div()
        .attrClass("flex flex-wrap items-center gap-3")
        .h2()
        .text(name)
        .__()
        .of(div -> {
          if (badge != null) {
            div.span().attrClass("badge").addAttr("data-variant", variant).text(badge).__();
          }
        })
        .__();
  }

  /**
   * @param remote whether the Services are of another Workstation, so a row can offer an Alias
   */
  private static void services(Div<?> region, List<Service> all, Filters filters, boolean remote) {
    List<Service> services = filters.of(all);
    if (services.isEmpty()) {
      if (all.isEmpty()) {
        EmptyState.render(
            region,
            "No Services yet.",
            remote
                ? "Only the Workstation of a Service can add it."
                : "Click New Service to add one.");
      } else {
        EmptyState.render(
            region,
            "No Services match the filters.",
            "Choose Every state and Every scope to see all Services.");
      }
      return;
    }
    region
        .div()
        .attrClass("catalog-services")
        .table()
        .attrClass("table catalog-table")
        .thead()
        .tr()
        .th()
        .text("Service")
        .__()
        .th()
        .text("State")
        .__()
        .th()
        .text("Hostname")
        .__()
        .th()
        .text("Instances")
        .__()
        .of(head -> head.th().span().attrClass("sr-only").text("Actions").__().__())
        .__()
        .__()
        .tbody()
        .of(body -> services.forEach(service -> row(body, service, remote)))
        .__()
        .__()
        .__();
  }

  private static void row(Tbody<?> body, Service service, boolean remote) {
    body.tr()
        .td()
        .a()
        .attrClass("catalog-service-link")
        .attrHref(Layout.attr(ServicePage.path(service)))
        .text(service.name())
        .__()
        .__()
        .td()
        .of(cell -> ServiceViews.state(cell, service.state()))
        .__()
        .td()
        .of(cell -> {
          if (service.hostname() == null) {
            cell.span().attrClass("text-muted-foreground").text("No hostname").__();
          } else {
            ServiceViews.hostname(cell, service);
          }
        })
        .__()
        .td()
        .of(cell -> {
          if (service.empty()) {
            cell.span().attrClass("badge").addAttr("data-variant", "outline").text("Empty").__();
          } else {
            cell.text(
                service.instances().isEmpty()
                    ? "No Instances"
                    : service.instances().stream()
                            .filter(instance -> instance.health() == Health.PASSING)
                            .count()
                        + " of "
                        + service.instances().size()
                        + " passing");
          }
        })
        .__()
        .td()
        .of(cell -> {
          if (!remote) {
            cell.a()
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .attrHref(Layout.attr(ServicePage.path(service)))
                .text("View")
                .__();
          } else if (service.sharedWithYard()) {
            AliasRemoteServicePage.button(cell, service);
          } else {
            cell.attrClass("catalog-spare");
          }
        })
        .__()
        .__();
  }

  private static String scopeLabel(Scope scope) {
    return switch (scope) {
      case LOCAL -> "Local";
      case YARD -> "Yard";
    };
  }
}
