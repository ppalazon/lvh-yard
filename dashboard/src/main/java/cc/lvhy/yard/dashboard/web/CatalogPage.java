package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.reachability.TraefikSettings;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.HomeWorkstation;
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
   * A {@code null} filter does not filter. Both stay in the page's address, as {@code
   * ?workstation=<name>&state=<state>}.
   */
  record Filters(String workstation, ServiceState state) {

    boolean shows(String name) {
      return workstation == null || workstation.equals(name);
    }

    List<Service> of(List<Service> services) {
      return state == null
          ? services
          : services.stream().filter(service -> service.state() == state).toList();
    }

    String path() {
      return UriComponentsBuilder.fromPath("/")
          .queryParamIfPresent("workstation", Optional.ofNullable(workstation))
          .queryParamIfPresent("state", Optional.ofNullable(state).map(ServiceState::value))
          .encode()
          .build()
          .toUriString();
    }
  }

  static String render(
      Workstation workstation, Yard yard, Filters filters, TraefikSettings traefik) {
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
                        region -> workstations(region, yard, filters, traefik)))
                .__());
  }

  private static void workstations(
      Div<?> region, Yard yard, Filters filters, TraefikSettings traefik) {
    if (filters.shows(yard.home().name())) {
      home(region, yard.home(), filters, traefik);
    }
    List<YardWorkstation> remotes =
        yard.remotes().stream().filter(remote -> filters.shows(remote.name())).toList();
    remotes.forEach(remote -> remote(region, remote, filters, traefik));
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
        .__();
  }

  private static void home(
      Div<?> region, HomeWorkstation home, Filters filters, TraefikSettings traefik) {
    region
        .div()
        .attrClass("catalog-workstation space-y-2")
        .of(group -> {
          heading(group, home.name(), "secondary", "This Workstation");
          services(group, home.services(), filters, false, traefik);
        })
        .__();
  }

  private static void remote(
      Div<?> region, YardWorkstation remote, Filters filters, TraefikSettings traefik) {
    region
        .div()
        .attrClass("catalog-workstation space-y-2")
        .of(group -> {
          if (remote.unreachable()) {
            heading(group, remote.name(), "destructive", "Workstation unreachable");
            group
                .p()
                .text(
                    "Its Consul does not answer. Its Services show again when it does."
                        + " To see why, run ")
                .code()
                .attrClass("rounded bg-muted px-2 py-1 font-mono")
                .text("lvh-yard ws compose logs consul")
                .__()
                .text(".")
                .__();
          } else {
            heading(group, remote.name(), null, null);
            services(group, remote.services(), filters, true, traefik);
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
  private static void services(
      Div<?> region, List<Service> all, Filters filters, boolean remote, TraefikSettings traefik) {
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
            region, "No Services in this state.", "Choose Every state to see all Services.");
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
        .of(head -> {
          if (remote) {
            head.th().span().attrClass("sr-only").text("Actions").__().__();
          }
        })
        .__()
        .__()
        .tbody()
        .of(body -> services.forEach(service -> row(body, service, remote, traefik)))
        .__()
        .__()
        .__();
  }

  private static void row(Tbody<?> body, Service service, boolean remote, TraefikSettings traefik) {
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
            ServiceViews.hostname(cell, service, traefik);
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
        .of(tr -> {
          if (remote) {
            tr.td()
                .of(cell -> {
                  if (service.sharedWithYard()) {
                    AliasRemoteServicePage.button(cell, service);
                  }
                })
                .__();
          }
        })
        .__();
  }
}
