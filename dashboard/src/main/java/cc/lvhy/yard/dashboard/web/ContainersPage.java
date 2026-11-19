package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.CONTAINERS_CHANGED;
import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.ListedContainer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.web.util.UriComponentsBuilder;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.Form;

/** Compose targets follow every replica, while their container rows show Docker's state. */
final class ContainersPage {

  static final String PATH = "/containers";

  private ContainersPage() {}

  /** A label and a value in the page's address for a choice of a filter. */
  private interface Choice {
    String value();

    String label();
  }

  /** Which containers the State selector shows. */
  enum StateFilter implements Choice {
    RUNNING("running", "Running"),
    STOPPED("stopped", "Stopped"),
    ALL("all", "All");

    private final String value;
    private final String label;

    StateFilter(String value, String label) {
      this.value = value;
      this.label = label;
    }

    @Override
    public String value() {
      return value;
    }

    @Override
    public String label() {
      return label;
    }

    static Optional<StateFilter> named(String value) {
      return Arrays.stream(values()).filter(filter -> filter.value.equals(value)).findFirst();
    }

    boolean shows(ListedContainer container) {
      return this == ALL || (this == RUNNING) == container.running();
    }
  }

  /** Which containers and Compose services the Followed selector shows. */
  enum FollowedFilter implements Choice {
    NO("no", "Not followed"),
    YES("yes", "Followed"),
    ALL("all", "All");

    private final String value;
    private final String label;

    FollowedFilter(String value, String label) {
      this.value = value;
      this.label = label;
    }

    @Override
    public String value() {
      return value;
    }

    @Override
    public String label() {
      return label;
    }

    static Optional<FollowedFilter> named(String value) {
      return Arrays.stream(values()).filter(filter -> filter.value.equals(value)).findFirst();
    }

    boolean shows(boolean followed) {
      return this == ALL || (this == YES) == followed;
    }
  }

  /**
   * Both stay in the page's address, as {@code ?state=<state>&followed=<followed>}, except the
   * defaults: running containers that no Container Binding follows.
   */
  record Filters(StateFilter state, FollowedFilter followed) {

    static final Filters DEFAULT = new Filters(StateFilter.RUNNING, FollowedFilter.NO);

    String path() {
      return UriComponentsBuilder.fromPath(PATH)
          .queryParamIfPresent(
              "state",
              Optional.of(state).filter(chosen -> chosen != DEFAULT.state).map(StateFilter::value))
          .queryParamIfPresent(
              "followed",
              Optional.of(followed)
                  .filter(chosen -> chosen != DEFAULT.followed)
                  .map(FollowedFilter::value))
          .build()
          .toUriString();
    }
  }

  static String render(
      Workstation workstation,
      List<ListedContainer> containers,
      Filters filters,
      String dockerProblem) {
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
                .__()
                .of(page -> filterForm(page, filters))
                .of(page ->
                    Layout.refreshed(
                        page,
                        "containers",
                        filters.path(),
                        List.of(CONTAINERS_CHANGED, YARD_CHANGED),
                        region -> listing(region, workstation, containers, filters, dockerProblem)))
                .__());
  }

  /** A Compose service is followed when any of its containers is, as its Create Service link is. */
  private static boolean followed(
      ListedContainer container, Map<String, Set<String>> composeFollowers) {
    if (container.composeProject() != null && container.composeService() != null) {
      return composeFollowers.containsKey(
          container.composeProject() + "/" + container.composeService());
    }
    return !container.followedBy().isEmpty();
  }

  private static String empty(boolean running, FollowedFilter followed) {
    return "No "
        + (running ? "running" : "stopped")
        + " containers"
        + switch (followed) {
          case NO -> " that are not followed";
          case YES -> " that are followed";
          case ALL -> "";
        }
        + ".";
  }

  /** The selectors sit outside the refreshed region, so a refresh keeps what you chose. */
  private static void filterForm(Div<?> page, Filters filters) {
    page.form()
        .attrClass("container-filters flex flex-wrap items-end gap-3")
        .addAttr("hx-get", PATH)
        .addAttr("hx-trigger", "change")
        .addAttr("hx-target", "#containers")
        .addAttr("hx-select", "#containers")
        .addAttr("hx-swap", "outerHTML")
        .addAttr("hx-push-url", "true")
        .addAttr("hx-status:4xx", "swap:none")
        .addAttr("hx-status:5xx", "swap:none")
        .of(form -> selector(form, "state", "State", StateFilter.values(), filters.state()))
        .of(form ->
            selector(form, "followed", "Followed", FollowedFilter.values(), filters.followed()))
        .__();
  }

  private static <T extends Choice> void selector(
      Form<?> form, String name, String label, T[] choices, T chosen) {
    form.div()
        .attrClass("flex min-w-0 flex-col gap-1.5 md:flex-row md:items-center md:gap-3")
        .label()
        .attrFor(name)
        .text(label)
        .__()
        .select()
        .attrClass("select")
        .attrId(name)
        .attrName(name)
        .of(select ->
            Arrays.stream(choices)
                .forEach(choice ->
                    select
                        .option()
                        .attrValue(choice.value())
                        .of(option -> option.attrSelected(choice == chosen))
                        .text(choice.label())
                        .__()))
        .__()
        .__();
  }

  private static void listing(
      Div<?> region,
      Workstation workstation,
      List<ListedContainer> containers,
      Filters filters,
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
          .text("The list refreshes when Docker answers.")
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
    List<ListedContainer> shown =
        ordered.stream()
            .filter(container -> filters.state().shows(container))
            .filter(container -> filters.followed().shows(followed(container, composeFollowers)))
            .toList();
    region
        .div()
        .attrClass("container-list")
        .of(list -> {
          if (filters.state() != StateFilter.STOPPED) {
            stateSection(list, workstation, shown, composeFollowers, filters, true);
          }
          if (filters.state() != StateFilter.RUNNING) {
            stateSection(list, workstation, shown, composeFollowers, filters, false);
          }
        })
        .__();
  }

  private static void stateSection(
      FlowContent<?, ?> parent,
      Workstation workstation,
      List<ListedContainer> containers,
      Map<String, Set<String>> composeFollowers,
      Filters filters,
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
            section.p().attrClass("container-empty").text(empty(running, filters.followed())).__();
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
