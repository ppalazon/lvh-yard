package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.UnfollowedContainer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

/** The running containers that no Container Binding follows. The list refreshes itself. */
final class ContainersPage {

  static final String PATH = "/containers";

  private ContainersPage() {}

  /**
   * @param containers in the order to show them, Compose projects first
   * @param dockerProblem why Docker cannot be read, or {@code null} when it answered
   */
  static String render(
      Workstation workstation, List<UnfollowedContainer> containers, String dockerProblem) {
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
                .of(content ->
                    Layout.refreshed(
                        content,
                        "containers",
                        PATH,
                        region -> list(region, containers, dockerProblem))));
  }

  private static void list(
      Div<?> region, List<UnfollowedContainer> containers, String dockerProblem) {
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
    byProject.forEach((project, inProject) -> group(region, project, inProject));
  }

  private static void group(Div<?> region, String project, List<UnfollowedContainer> containers) {
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
        .__()
        .__()
        .tbody()
        .of(body -> containers.forEach(container -> row(body.tr(), compose, container)))
        .__()
        .__()
        .__()
        .__();
  }

  private static void row(Tr<?> row, boolean compose, UnfollowedContainer container) {
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
        .__();
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
