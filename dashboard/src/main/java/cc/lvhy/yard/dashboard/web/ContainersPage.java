package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.CONTAINERS_CHANGED;
import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.ListedContainer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.FlowContent;

/** Compose targets follow every replica, while their container rows show Docker's state. */
final class ContainersPage {

  static final String PATH = "/containers";

  private ContainersPage() {}

  static String render(
      Workstation workstation, List<ListedContainer> containers, String dockerProblem) {
    return Layout.page(
        "Containers",
        workstation.name(),
        Layout.Section.CONTAINERS,
        main ->
            main.div()
                .attrClass("containers-page")
                .header()
                .attrClass("containers-heading")
                .h1()
                .text("Containers")
                .__()
                .p()
                .text("Create a Service from a container or Compose service on this Workstation.")
                .__()
                .__()
                .of(page ->
                    Layout.refreshed(
                        page,
                        "containers",
                        PATH,
                        List.of(CONTAINERS_CHANGED, YARD_CHANGED),
                        region -> listing(region, workstation, containers, dockerProblem)))
                .__());
  }

  private static void listing(
      Div<?> region,
      Workstation workstation,
      List<ListedContainer> containers,
      String dockerProblem) {
    if (dockerProblem != null) {
      region
          .div()
          .attrClass("alert")
          .addAttr("data-variant", "destructive")
          .addAttr("role", "status")
          .h2()
          .text("Cannot read Docker containers")
          .__()
          .p()
          .text(dockerProblem)
          .__()
          .p()
          .text("The list refreshes when Docker is available again.")
          .__()
          .__();
      return;
    }
    List<ListedContainer> ordered =
        containers.stream().sorted(Comparator.comparing(ListedContainer::name)).toList();
    Map<String, Set<String>> composeFollowers = new TreeMap<>();
    // A name Binding on one replica also hides the action for its Compose target in both states.
    for (ListedContainer container : ordered) {
      if (container.composeProject() != null
          && container.composeService() != null
          && !container.followedBy().isEmpty()) {
        composeFollowers
            .computeIfAbsent(
                container.composeProject() + "/" + container.composeService(),
                ignored -> new TreeSet<>())
            .addAll(container.followedBy());
      }
    }
    region
        .div()
        .attrClass("container-list")
        .of(list -> {
          stateSection(list, workstation, ordered, composeFollowers, true);
          stateSection(list, workstation, ordered, composeFollowers, false);
        })
        .__();
  }

  private static void stateSection(
      FlowContent<?, ?> parent,
      Workstation workstation,
      List<ListedContainer> containers,
      Map<String, Set<String>> composeFollowers,
      boolean running) {
    Map<String, Map<String, List<ListedContainer>>> projects = new TreeMap<>();
    List<ListedContainer> standalone = new ArrayList<>();
    int count = 0;
    for (ListedContainer container : containers) {
      if (container.running() != running) {
        continue;
      }
      count++;
      if (container.composeProject() != null && container.composeService() != null) {
        projects
            .computeIfAbsent(container.composeProject(), ignored -> new TreeMap<>())
            .computeIfAbsent(container.composeService(), ignored -> new ArrayList<>())
            .add(container);
      } else {
        standalone.add(container);
      }
    }
    int total = count;
    String state = running ? "running" : "stopped";
    parent
        .section()
        .attrClass("container-state")
        .addAttr("data-state", state)
        .addAttr("aria-labelledby", state + "-containers")
        .div()
        .attrClass("container-state-heading")
        .h2()
        .attrId(state + "-containers")
        .text(running ? "Running" : "Stopped")
        .__()
        .span()
        .attrClass("text-sm text-muted-foreground")
        .text(total + (total == 1 ? " container" : " containers"))
        .__()
        .__()
        .of(section -> {
          if (total == 0) {
            section
                .p()
                .attrClass("container-empty")
                .text(running ? "No running containers." : "No stopped containers.")
                .__();
            return;
          }
          for (var project : projects.entrySet()) {
            section
                .div()
                .attrClass("container-project")
                .h3()
                .attrClass("container-project-heading")
                .span()
                .attrClass("text-sm font-normal text-muted-foreground")
                .text("Compose project")
                .__()
                .span()
                .text(project.getKey())
                .__()
                .__()
                .of(group -> {
                  for (var service : project.getValue().entrySet()) {
                    String target = project.getKey() + "/" + service.getKey();
                    Set<String> followedBy = composeFollowers.getOrDefault(target, Set.of());
                    int replicas = service.getValue().size();
                    group
                        .div()
                        .attrClass("container-compose-service")
                        .div()
                        .attrClass("container-target-heading")
                        .h4()
                        .text(service.getKey())
                        .__()
                        .of(heading -> {
                          if (followedBy.isEmpty()) {
                            createLink(heading, target);
                          }
                        })
                        .__()
                        .p()
                        .attrClass("container-target-hint")
                        .text("Follows every container of " + target + ".")
                        .__()
                        .of(targetGroup -> followers(targetGroup, workstation, followedBy))
                        .custom("details")
                        .attrClass("container-details")
                        .addAttr(
                            "data-preserve-open",
                            Layout.attr("containers:" + state + ":compose:" + target))
                        .custom("summary")
                        .text(replicas + (replicas == 1 ? " container" : " containers"))
                        .__()
                        .div()
                        .attrClass("container-replicas")
                        .of(rows -> {
                          for (ListedContainer container : service.getValue()) {
                            containerRow(rows, workstation, container, true);
                          }
                        })
                        .__()
                        .__()
                        .__();
                  }
                })
                .__();
          }
          if (!standalone.isEmpty()) {
            section
                .div()
                .attrClass("container-project container-standalone")
                .h3()
                .attrClass("container-project-heading")
                .text("Other containers")
                .__()
                .of(group -> {
                  for (ListedContainer container : standalone) {
                    group
                        .div()
                        .attrClass("container-standalone-target")
                        .div()
                        .attrClass("container-target-heading")
                        .h4()
                        .text(container.name())
                        .__()
                        .of(heading -> {
                          if (container.followedBy().isEmpty()) {
                            createLink(heading, container.name());
                          }
                        })
                        .__()
                        .of(targetGroup ->
                            followers(targetGroup, workstation, container.followedBy()))
                        .custom("details")
                        .attrClass("container-details")
                        .addAttr(
                            "data-preserve-open",
                            Layout.attr("containers:" + state + ":container:" + container.name()))
                        .custom("summary")
                        .text("Container details")
                        .__()
                        .of(details -> containerRow(details, workstation, container, false))
                        .__()
                        .__();
                  }
                })
                .__();
          }
        })
        .__();
  }

  private static void createLink(FlowContent<?, ?> parent, String target) {
    parent
        .a()
        .attrClass("btn container-create")
        .addAttr("data-size", "sm")
        .addAttr("data-variant", "outline")
        .attrHref(
            Layout.attr(
                PATH + "/services/new?target=" + URLEncoder.encode(target, StandardCharsets.UTF_8)))
        .addAttr("aria-label", Layout.attr("Create Service for " + target))
        .text("Create Service")
        .__();
  }

  private static void containerRow(
      FlowContent<?, ?> parent,
      Workstation workstation,
      ListedContainer container,
      boolean replica) {
    parent
        .div()
        .attrClass(replica ? "container-row container-replica-row" : "container-row")
        .of(row -> {
          if (replica || container.health() != null) {
            row.div()
                .attrClass("container-name")
                .of(name -> {
                  if (replica) {
                    name.p().text(container.name()).__();
                  }
                  if (container.health() != null) {
                    name.p()
                        .attrClass("text-sm font-normal text-muted-foreground")
                        .text("Healthcheck: " + container.health().value())
                        .__();
                  }
                })
                .__();
          }
        })
        .div()
        .attrClass("container-ports")
        .of(ports -> ports(ports, container))
        .__()
        .of(row -> {
          if (container.followedBy().isEmpty()) {
            row.p()
                .attrClass("text-sm text-muted-foreground")
                .text(container.running() ? "Unfollowed" : "Not followed")
                .__();
          } else if (replica) {
            followers(row, workstation, container.followedBy());
          }
        })
        .__();
  }

  private static void followers(
      FlowContent<?, ?> parent, Workstation workstation, Collection<String> services) {
    if (services.isEmpty()) {
      return;
    }
    parent
        .div()
        .attrClass("container-followers")
        .span()
        .attrClass("text-muted-foreground")
        .text("Followed by")
        .__()
        .ul()
        .of(list -> {
          for (String service : services) {
            list.li()
                .a()
                .attrHref(Layout.attr(ServicePage.path(workstation.name(), service)))
                .text(service)
                .__()
                .__();
          }
        })
        .__()
        .__();
  }

  private static void ports(Div<?> parent, ListedContainer container) {
    if (container.ports().isEmpty()) {
      parent.p().attrClass("text-sm text-muted-foreground").text("No known ports").__();
      return;
    }
    parent
        .table()
        .attrClass("container-port-table")
        .addAttr("aria-label", Layout.attr("Port mappings for " + container.name()))
        .thead()
        .tr()
        .th()
        .addAttr("scope", "col")
        .text("Container Port")
        .__()
        .th()
        .addAttr("scope", "col")
        .text("Host Port")
        .__()
        .th()
        .addAttr("scope", "col")
        .text("Bind IP")
        .__()
        .__()
        .__()
        .tbody()
        .of(body -> {
          for (ListedContainer.ContainerPort port : container.ports()) {
            body.tr()
                .td()
                .text(port.containerPort())
                .__()
                .td()
                .text(port.hostPort() == null ? "Not published" : port.hostPort().toString())
                .__()
                .td()
                .text(bindIp(port))
                .__()
                .__();
          }
        })
        .__()
        .__();
  }

  private static String bindIp(ListedContainer.ContainerPort port) {
    if (port.hostPort() == null) {
      return "—";
    }
    String ip = port.hostIp();
    if (ip == null || ip.isBlank()) {
      return "Unknown";
    }
    return ip.contains(":") && !ip.startsWith("[") ? "[" + ip + "]" : ip;
  }
}
