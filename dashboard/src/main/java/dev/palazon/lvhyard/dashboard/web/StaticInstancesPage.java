package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.List;
import org.xmlet.htmlapifaster.Main;

final class StaticInstancesPage {

  static final String PATH = "/static-instances";

  /** The address of the Register page, which this page replaces. */
  static final String OLD_PATH = "/register";

  static final String NEW_PATH = PATH + "/new";

  private static final FormPage.Spec REGISTER =
      new FormPage.Spec(
          Layout.Section.STATIC_INSTANCES,
          "Register a Static Instance",
          null,
          NEW_PATH,
          PATH,
          "Register");

  private StaticInstancesPage() {}

  /**
   * @param services this Workstation's Services
   */
  static String render(Workstation workstation, List<Service> services) {
    return Layout.page(
        "Static Instances",
        workstation.name(),
        Layout.Section.STATIC_INSTANCES,
        main ->
            main.h1()
                .text("Static Instances")
                .__()
                .p()
                .text("Instances at a fixed host and port, on " + workstation.name() + ".")
                .__()
                .of(content -> FormPage.openLink(content, REGISTER))
                .of(content -> list(content, services)));
  }

  /**
   * @param defaultHost the host a Static Instance gets when none is typed
   * @param error why the last post failed, shown at its field, or {@code null}
   */
  static String renderNew(
      Workstation workstation,
      String name,
      InstanceForm.Fields fields,
      String defaultHost,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        REGISTER,
        form -> {},
        section -> {
          InstanceForm.text(
              section,
              "name",
              "Service name",
              name,
              "grafana",
              "Lowercase letters, digits and -.",
              error);
          InstanceForm.fields(section, fields, defaultHost, error);
        });
  }

  private record Row(Service service, Instance instance) {}

  private static void list(Main<?> main, List<Service> services) {
    List<Row> rows =
        services.stream()
            .flatMap(service ->
                service.instances().stream()
                    .filter(Instance::staticInstance)
                    .map(instance -> new Row(service, instance)))
            .toList();
    if (rows.isEmpty()) {
      main.p().text("No Static Instances yet.").__();
      return;
    }
    main.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .th()
        .text("Service")
        .__()
        .th()
        .text("Address")
        .__()
        .th()
        .text("Health")
        .__()
        .th()
        .of(cell -> cell.span().attrClass("sr-only").text("Actions").__())
        .__()
        .__()
        .__()
        .tbody()
        .of(body ->
            rows.forEach(row ->
                body.tr()
                    .td()
                    .a()
                    .attrHref(Layout.attr(ServicePage.path(row.service())))
                    .attrClass("underline")
                    .text(row.service().name())
                    .__()
                    .__()
                    .td()
                    .text(row.instance().address())
                    .__()
                    .td()
                    .of(cell -> InstanceViews.health(cell, row.instance().health()))
                    .__()
                    .td()
                    .div()
                    .attrClass("flex justify-end")
                    .a()
                    .attrHref(Layout.attr(EditInstancePage.path(row.service(), row.instance())))
                    .attrClass("btn")
                    .addAttr("data-variant", "outline")
                    .addAttr("data-size", "sm")
                    .text("Edit")
                    .__()
                    .__()
                    .__()
                    .__()))
        .__()
        .__()
        .__();
  }
}
