package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.CreateContainerService;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.FlowContent;

/** A new Service that follows one container or Compose service. */
final class CreateContainerServicePage {
  static final String PATH = "/containers/services/new";
  static final String ACTION = "/containers/services";

  private static final FormPage.Spec SPEC =
      new FormPage.Spec(
          Layout.Section.CONTAINERS,
          "Create Service",
          "Follow a container or Compose service and choose who gets a hostname.",
          PATH,
          ACTION,
          "Create Service");

  record Port(int number, List<Integer> hostPorts) {
    String label() {
      return hostPorts.isEmpty()
          ? "not published"
          : "published on " + String.join(", ", hostPorts.stream().map(String::valueOf).toList());
    }
  }

  record Target(String value, String port, List<Port> ports) {}

  record Fields(String name, Target target, boolean publish, String scope) {}

  private CreateContainerServicePage() {}

  static String render(
      Workstation workstation,
      Fields fields,
      String dockerProblem,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        SPEC,
        form -> {},
        section -> {
          if (dockerProblem != null) {
            section
                .div()
                .attrClass("alert")
                .addAttr("role", "status")
                .h3()
                .text("The dashboard cannot read Docker")
                .__()
                .section()
                .text(dockerProblem + " You can still type the Container Port.")
                .__()
                .__();
          }
          InstanceForm.text(
              section,
              "name",
              "Service name",
              fields.name(),
              "grafana",
              "Use a new name with lowercase letters, digits and -.",
              error);
          target(section, fields.target(), error);
          section
              .div()
              .attrClass("field")
              .label()
              .attrClass("flex items-center gap-3")
              .input()
              .attrType(EnumTypeInputType.CHECKBOX)
              .attrName("publish")
              .attrValue("true")
              .attrChecked(fields.publish())
              .__()
              .text("Publish")
              .__()
              .p()
              .attrClass("text-muted-foreground text-sm")
              .text("Give the Service a hostname without starting or stopping containers.")
              .__()
              .__();
          InstanceForm.field(
              section,
              "scope",
              "Scope",
              "Local gives a hostname only on this Workstation.",
              error,
              field ->
                  field
                      .select()
                      .attrClass("select")
                      .attrId("scope")
                      .attrName("scope")
                      .option()
                      .attrValue("local")
                      .attrSelected("local".equals(fields.scope()))
                      .text("Local")
                      .__()
                      .option()
                      .attrValue("yard")
                      .attrSelected("yard".equals(fields.scope()))
                      .text("Yard")
                      .__()
                      .__());
          section.script().attrSrc("/bind.js").attrDefer(true).__();
        });
  }

  private static void target(
      FlowContent<?, ?> section, Target target, InvalidRegistrationException error) {
    String field = "containerPort";
    section
        .div()
        .attrClass("flex flex-col gap-3 border-l-4 border-primary pl-4")
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("target")
        .attrValue(Layout.attr(target.value()))
        .__()
        .h2()
        .attrClass("break-words text-base font-semibold")
        .text(target.value())
        .__()
        .p()
        .attrClass("text-muted-foreground text-sm")
        .text(
            target.value().contains("/")
                ? "Follows every container of " + target.value() + "."
                : "Follows this container by name.")
        .__()
        .of(group ->
            InstanceForm.field(
                group,
                field,
                "Container Port",
                target.ports().isEmpty()
                    ? "No ports are known. Type the port inside the container."
                    : "Choose an open port, or type another port inside the container.",
                error,
                control -> {
                  control
                      .div()
                      .attrClass("relative")
                      .input()
                      .attrType(EnumTypeInputType.TEXT)
                      .attrClass("input")
                      .attrId(field)
                      .attrName(field)
                      .attrValue(Layout.attr(target.port()))
                      .addAttr("data-port-combobox", "")
                      .addAttr("inputmode", "numeric")
                      .addAttr("autocomplete", "off")
                      .addAttr("role", "combobox")
                      .addAttr("aria-autocomplete", "list")
                      .addAttr("aria-expanded", "false")
                      .addAttr("aria-controls", field + "-options")
                      .of(input -> {
                        if (error != null && error.field().equals(field)) {
                          input
                              .addAttr("aria-invalid", "true")
                              .addAttr("aria-describedby", field + "-error");
                        }
                      })
                      .__()
                      .ul()
                      .attrId(field + "-options")
                      .attrClass(
                          "bg-popover text-popover-foreground absolute z-10 mt-1 max-h-72 w-full"
                              + " overflow-auto rounded-md border p-1 shadow-md")
                      .addAttr("role", "listbox")
                      .addAttr("aria-label", "Container Ports")
                      .addAttr("hidden", "")
                      .of(list -> {
                        for (Port port : target.ports()) {
                          list.li()
                              .attrClass(
                                  "flex cursor-default justify-between gap-2 rounded-sm px-2 py-1.5"
                                      + " text-sm aria-selected:bg-accent")
                              .addAttr("role", "option")
                              .addAttr("data-value", String.valueOf(port.number()))
                              .span()
                              .text(String.valueOf(port.number()))
                              .__()
                              .span()
                              .attrClass("text-muted-foreground")
                              .text(port.label())
                              .__()
                              .__();
                        }
                      })
                      .__()
                      .__();
                }))
        .__();
  }

  static String failure(Workstation workstation, CreateContainerService.Result result) {
    return Layout.page(
        "Service creation stopped",
        workstation.name(),
        Layout.Section.CONTAINERS,
        main -> {
          main.h1().text("Service creation stopped").__();
          main.div()
              .attrClass("alert")
              .addAttr("role", "alert")
              .h2()
              .text("Not all changes were saved")
              .__()
              .section()
              .text(result.failure())
              .__()
              .__();
          main.p()
              .text(
                  "Saved changes stay. Nothing was rolled back. Check the Service before trying"
                      + " again.")
              .__();
          main.ul()
              .attrClass("list-disc pl-6")
              .of(list -> result.completed().forEach(step -> list.li().text(step).__()))
              .__();
          main.div()
              .attrClass("flex flex-wrap gap-3")
              .of(actions -> {
                if (result.created()) {
                  actions
                      .a()
                      .attrClass("btn")
                      .attrHref(Layout.attr(ServicePage.path(workstation.name(), result.name())))
                      .text("View Service")
                      .__();
                }
                actions
                    .a()
                    .attrClass("btn")
                    .addAttr("data-variant", "outline")
                    .attrHref(ContainersPage.PATH)
                    .text("Containers")
                    .__();
              })
              .__();
        });
  }
}
