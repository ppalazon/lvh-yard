package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.HomeWorkstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
import dev.palazon.lvhyard.dashboard.services.Yard;
import dev.palazon.lvhyard.dashboard.services.YardWorkstation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.web.util.UriComponentsBuilder;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tbody;

/**
 * The Catalog: every Service in the Yard, grouped by Home Workstation, this one first. Each Service
 * has its state and one row per Instance, or one empty row if it has none. A Workstation that does
 * not answer is shown as unreachable. A form above filters the Services by Workstation and by
 * state; the Services refresh themselves, with the filters.
 */
final class CatalogPage {

  private CatalogPage() {}

  /**
   * Which Services the Catalog shows: only those of {@code workstation}, and only those in {@code
   * state}. A {@code null} one does not filter. Both stay in the page's address, as {@code
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

    /** The Catalog's address with these filters, such as {@code /?state=healthy}. */
    String path() {
      return UriComponentsBuilder.fromPath("/")
          .queryParamIfPresent("workstation", Optional.ofNullable(workstation))
          .queryParamIfPresent("state", Optional.ofNullable(state).map(ServiceState::value))
          .encode()
          .build()
          .toUriString();
    }
  }

  static String render(Workstation workstation, Yard yard, Filters filters) {
    return Layout.page(
        "Catalog",
        workstation.name(),
        main ->
            main.div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text("Catalog")
                .__()
                .div()
                .attrClass("flex flex-wrap gap-2")
                .a()
                .attrHref(AliasesPage.PATH)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text("Aliases")
                .__()
                .a()
                .attrHref(RegisterPage.PATH)
                .attrClass("btn")
                .text("Register")
                .__()
                .__()
                .__()
                .p()
                .text(
                    "Workstation "
                        + workstation.name()
                        + ", base domain "
                        + workstation.baseDomain()
                        + ".")
                .__()
                .of(content -> filterForm(content, yard, filters))
                .of(content ->
                    Layout.refreshed(
                        content,
                        "catalog",
                        filters.path(),
                        region -> workstations(region, yard, filters))));
  }

  /**
   * Each Workstation the filters show, with its Services, or that the Yard has no Workstation of
   * that name.
   */
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

  /**
   * The form that filters the Catalog by Workstation and by state; it loads the Catalog with them
   * in its address.
   */
  private static void filterForm(Main<?> main, Yard yard, Filters filters) {
    List<String> names =
        Stream.concat(
                Stream.of(yard.home().name()), yard.remotes().stream().map(YardWorkstation::name))
            .toList();
    main.form()
        .attrClass("form flex flex-wrap items-end gap-4")
        .addAttr("method", "get")
        .addAttr("action", "/")
        .div()
        .attrClass("field")
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
        .attrClass("field")
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
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .text("Filter")
        .__()
        .__();
  }

  /** This Workstation's heading, and its Services. */
  private static void home(Div<?> region, HomeWorkstation home, Filters filters) {
    heading(region, home.name(), "secondary", "This Workstation");
    services(region, home.services(), filters);
  }

  /** Another Workstation's heading, and its Services, or why they are not known. */
  private static void remote(Div<?> region, YardWorkstation remote, Filters filters) {
    if (remote.unreachable()) {
      heading(region, remote.name(), "destructive", "Workstation unreachable");
      region.p().text("Its Consul does not answer. Its Services show again when it does.").__();
      return;
    }
    heading(region, remote.name(), null, null);
    services(region, remote.services(), filters);
  }

  /** A Workstation's name as a heading, with a badge beside it when {@code badge} is not null. */
  private static void heading(Div<?> region, String name, String variant, String badge) {
    region
        .div()
        .attrClass("mt-4 flex flex-wrap items-center gap-3")
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

  /** The Services the filters show, or why there are none. */
  private static void services(Div<?> region, List<Service> all, Filters filters) {
    List<Service> services = filters.of(all);
    if (services.isEmpty()) {
      region.p().text(all.isEmpty() ? "No Services yet." : "No Services in this state.").__();
      return;
    }
    region
        .div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
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
        .text("Instance")
        .__()
        .th()
        .text("Health")
        .__()
        .__()
        .__()
        .tbody()
        .of(body ->
            services.forEach(service -> {
              if (service.instances().isEmpty()) {
                row(body, service, null);
              }
              service.instances().forEach(instance -> row(body, service, instance));
            }))
        .__()
        .__()
        .__();
  }

  /**
   * A row for {@code instance} of {@code service}, or for the Service alone when {@code instance}
   * is null.
   */
  private static void row(Tbody<?> body, Service service, Instance instance) {
    body.tr()
        .td()
        .a()
        .attrHref(Layout.attr(ServicePage.path(service)))
        .text(service.name())
        .__()
        .of(cell -> ServiceViews.core(cell, service))
        .__()
        .td()
        .of(cell -> ServiceViews.state(cell, service.state()))
        .__()
        .td()
        .of(cell -> ServiceViews.hostname(cell, service))
        .__()
        .td()
        .text(instance == null ? "No Instances" : instance.address())
        .__()
        .td()
        .of(cell -> {
          if (instance != null) {
            InstanceViews.health(cell, instance.health());
          }
        })
        .__()
        .__();
  }
}
