package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.UnfollowedContainer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

/** The running containers that no Container Binding follows. The list refreshes itself. */
final class ContainersPage {

  static final String PATH = "/containers";

  record ImportFailure(String container, String reason) {}

  private ContainersPage() {}

  static String importPath(String container) {
    return PATH + "/" + container + "/import";
  }

  /**
   * @param containers in the order to show them, Compose projects first
   * @param dockerProblem why Docker cannot be read, or {@code null} when it answered
   * @param imported the Service that the one-click Import just gave a container, or {@code null}.
   *     It is said outside the refreshed list, so the refresh keeps it.
   * @param failure the import that failed, or {@code null}
   */
  static String render(
      Workstation workstation,
      List<UnfollowedContainer> containers,
      String dockerProblem,
      Service imported,
      ImportFailure failure) {
    return Layout.page(
        "Containers",
        workstation.name(),
        Layout.Section.CONTAINERS,
        main ->
            main.h1()
                .text("Containers")
                .__()
                .p()
                .text("Running containers that LVH Yard does not follow yet.")
                .__()
                .of(content -> {
                  if (imported != null) {
                    content
                        .div()
                        .attrClass("alert")
                        .addAttr("data-imported", Layout.attr(imported.name()))
                        .addAttr("role", "status")
                        .section()
                        .text("Imported into the Service ")
                        .a()
                        .attrClass("underline")
                        .attrHref(Layout.attr(ServicePage.path(imported)))
                        .text(imported.name())
                        .__()
                        .text(".")
                        .__()
                        .__();
                  }
                })
                .of(content -> {
                  if (failure == null) {
                    Layout.refreshed(
                        content,
                        "containers",
                        PATH,
                        region -> list(region, containers, dockerProblem, null));
                    return;
                  }
                  // A refresh would drop the failure, so this list stays as it is.
                  content
                      .div()
                      .attrId("containers")
                      .attrClass("flex flex-col gap-8")
                      .of(region -> list(region, containers, dockerProblem, failure))
                      .__();
                }));
  }

  private static void list(
      Div<?> region,
      List<UnfollowedContainer> containers,
      String dockerProblem,
      ImportFailure failure) {
    if (dockerProblem != null) {
      region
          .div()
          .attrClass("alert")
          .addAttr("data-variant", "destructive")
          .addAttr("role", "alert")
          .h3()
          .text("The dashboard cannot read Docker")
          .__()
          .section()
          .text(dockerProblem)
          .__()
          .__();
      return;
    }
    if (containers.isEmpty()) {
      region.p().text("Every running container is followed.").__();
      return;
    }
    // A plain container's project is "", so it is a group of its own, after the projects.
    Map<String, List<UnfollowedContainer>> byProject =
        containers.stream()
            .collect(
                Collectors.groupingBy(
                    container ->
                        container.composeProject() == null ? "" : container.composeProject(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    byProject.forEach((project, inProject) -> group(region, project, inProject, failure));
  }

  private static void group(
      Div<?> region, String project, List<UnfollowedContainer> containers, ImportFailure failure) {
    boolean compose = !project.isEmpty();
    region
        .div()
        .attrClass("flex flex-col gap-3")
        .addAttr("data-compose-project", Layout.attr(project))
        .h2()
        .text(compose ? "Compose project " + project : "Other containers")
        .__()
        .div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .of(row -> {
          if (compose) {
            row.th().text("Compose service").__();
          }
        })
        .th()
        .text("Container")
        .__()
        .th()
        .text("Ports")
        .__()
        .th()
        .text("Health")
        .__()
        .th()
        .text("Import")
        .__()
        .__()
        .__()
        .tbody()
        .of(body -> containers.forEach(container -> row(body.tr(), compose, container, failure)))
        .__()
        .__()
        .__()
        .__();
  }

  private static void row(
      Tr<?> row, boolean compose, UnfollowedContainer container, ImportFailure failure) {
    row.addAttr("data-container", Layout.attr(container.name()));
    if (compose) {
      row.td().attrClass("align-top").text(container.composeService()).__();
    }
    row.td()
        .attrClass("align-top")
        .text(container.name())
        .__()
        .td()
        .attrClass("align-top")
        .of(cell -> ports(cell, container.ports()))
        .__()
        .td()
        .attrClass("align-top")
        .of(cell -> health(cell, container.health()))
        .__()
        .td()
        .attrClass("align-top")
        .of(cell -> importActions(cell, container, failure))
        .__()
        .__();
  }

  /**
   * One click is enough only when the container has one port, as the port is then known. The choice
   * of name and port is a page of its own, so the 5 s refresh cannot reset what is typed.
   */
  private static void importActions(
      Td<?> cell, UnfollowedContainer container, ImportFailure failure) {
    List<Integer> ports = container.containerPorts();
    cell.div()
        .attrClass("flex flex-wrap gap-2")
        .of(actions -> {
          if (ports.size() == 1) {
            actions
                .form()
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(importPath(container.name())))
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .addAttr("data-size", "sm")
                .text("Import")
                .__()
                .__();
          }
        })
        .a()
        .attrClass("btn")
        .addAttr("data-variant", ports.size() == 1 ? "ghost" : "outline")
        .addAttr("data-size", "sm")
        .attrHref(Layout.attr(importPath(container.name())))
        .text(ports.size() == 1 ? "Choose name" : "Choose port")
        .__()
        .__();
    if (failure != null && failure.container().equals(container.name())) {
      cell.div()
          .attrClass("alert mt-2")
          .addAttr("data-variant", "destructive")
          .addAttr("role", "alert")
          .section()
          .text(failure.reason())
          .__()
          .__();
    }
  }

  /**
   * @param name the Service name as typed
   * @param port the container port as typed
   * @param error why the last import failed, shown at its field, or {@code null}
   */
  static String renderImport(
      Workstation workstation,
      UnfollowedContainer container,
      String name,
      String port,
      InvalidRegistrationException error) {
    return Layout.page(
        "Import " + container.name(),
        workstation.name(),
        Layout.Section.CONTAINERS,
        main ->
            main.h1()
                .text("Import " + container.name())
                .__()
                .p()
                .text(
                    "Binds the container to a Service. A new Service is published with Local"
                        + " Scope.")
                .__()
                .form()
                .attrClass("form flex max-w-md flex-col gap-6")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(importPath(container.name())))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "name",
                        "Service name",
                        name,
                        container.service(),
                        "Lowercase letters, digits and -.",
                        error))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "port",
                        "Container port",
                        port,
                        "80",
                        container.containerPorts().isEmpty()
                            ? "The container has no exposed port."
                            : "Its ports: " + container.containerPortList() + ".",
                        error))
                .div()
                .attrClass("flex gap-2")
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .text("Import")
                .__()
                .a()
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .attrHref(PATH)
                .text("Cancel")
                .__()
                .__()
                .__());
  }

  /** The port the import form proposes: the only one, else none. */
  static String proposedPort(UnfollowedContainer container) {
    List<Integer> ports = container.containerPorts();
    return ports.size() == 1 ? String.valueOf(ports.getFirst()) : "";
  }

  private static void ports(Td<?> cell, List<UnfollowedContainer.ContainerPort> ports) {
    if (ports.isEmpty()) {
      cell.text("No exposed port");
      return;
    }
    cell.ul()
        .of(list ->
            ports.forEach(port ->
                list.li()
                    .text(
                        port.hostPort() == null
                            ? port.containerPort() + " not published"
                            : port.containerPort() + " on host port " + port.hostPort())
                    .__()))
        .__();
  }

  private static void health(Td<?> cell, UnfollowedContainer.HealthcheckState health) {
    if (health == null) {
      cell.text("No HEALTHCHECK");
      return;
    }
    String variant =
        switch (health) {
          case HEALTHY -> "success";
          case UNHEALTHY -> "destructive";
          case STARTING -> "secondary";
        };
    cell.span().attrClass("badge").addAttr("data-variant", variant).text(health.value()).__();
  }
}
