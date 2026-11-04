package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Form;
import org.xmlet.htmlapifaster.Main;

final class StaticInstancesPage {

  static final String PATH = "/static-instances";

  /** The address of the Register page, which this page replaces. */
  static final String OLD_PATH = "/register";

  private StaticInstancesPage() {}

  /**
   * @param services this Workstation's Services
   * @param publish whether the "Publish a new Service" checkbox is on
   * @param defaultHost the host a Static Instance gets when none is typed
   */
  static String render(
      Workstation workstation,
      List<Service> services,
      String name,
      InstanceForm.Fields fields,
      boolean publish,
      String defaultHost,
      InvalidRegistrationException error) {
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
                .of(content -> list(content, services))
                .h2()
                .text("Register a Static Instance")
                .__()
                .form()
                .attrClass("form flex max-w-md flex-col gap-6")
                .addAttr("method", "post")
                .addAttr("action", PATH)
                .of(form ->
                    InstanceForm.text(
                        form,
                        "name",
                        "Service name",
                        name,
                        "grafana",
                        "Lowercase letters, digits and -.",
                        error))
                .of(form -> InstanceForm.fields(form, fields, defaultHost, error))
                .of(form -> publish(form, publish))
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn w-fit")
                .text("Register")
                .__()
                .__());
  }

  private record Row(Service service, Instance instance) {}

  private static void list(Main<?> main, List<Service> services) {
    List<Row> rows =
        services.stream()
            .flatMap(service ->
                service.instances().stream()
                    .filter(instance -> !instance.bound())
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

  /** A cleared checkbox sends nothing, so the form sends {@code publish} only when it is on. */
  private static void publish(Form<?> form, boolean checked) {
    form.label()
        .attrClass("label gap-3")
        .input()
        .attrType(EnumTypeInputType.CHECKBOX)
        .attrClass("input")
        .attrName("publish")
        .of(input -> {
          if (checked) {
            input.attrChecked(true);
          }
        })
        .__()
        .text("Publish a new Service")
        .__();
  }
}
