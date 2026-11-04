package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
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

  /** Why importing {@code container} failed. */
  record ImportFailure(String container, String reason) {}

  private ContainersPage() {}

  static String importPath(String container) {
    return PATH + "/" + container + "/import";
  }

  /**
   * @param containers in the order to show them, Compose projects first
   * @param dockerProblem why Docker cannot be read, or {@code null} when it answered
   * @param failure the import that failed, or {@code null}
   */
  static String render(
      Workstation workstation,
      List<UnfollowedContainer> containers,
      String dockerProblem,
      ImportFailure failure) {
    return Layout.page(
        "Containers",
        workstation.name(),
        main ->
            main.h1()
                .text("Containers")
                .__()
                .p()
                .text("Running containers that LVH Yard does not follow yet.")
                .__()
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
        .of(cell -> importer(cell, container, failure))
        .__()
        .__();
  }

  /** One click is enough only when the container has one port, as the port is then known. */
  private static void importer(Td<?> cell, UnfollowedContainer container, ImportFailure failure) {
    long ports =
        container.ports().stream()
            .map(UnfollowedContainer.ContainerPort::containerPort)
            .distinct()
            .count();
    if (ports == 1) {
      cell.form()
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
                            ? port.containerPort() + ", not published"
                            : port.containerPort() + ", on host port " + port.hostPort())
                    .__()))
        .__();
  }

  private static void health(Td<?> cell, String health) {
    if (health == null) {
      cell.text("No HEALTHCHECK");
      return;
    }
    String variant =
        switch (health) {
          case "healthy" -> "primary";
          case "unhealthy" -> "destructive";
          default -> "secondary";
        };
    cell.span().attrClass("badge").addAttr("data-variant", variant).text(health).__();
  }
}
