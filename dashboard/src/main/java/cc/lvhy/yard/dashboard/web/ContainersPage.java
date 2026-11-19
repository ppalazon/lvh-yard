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
import java.util.TreeMap;
import java.util.function.Consumer;
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

    /** Not followed is what still needs a Service: nothing follows it, or a known port is free. */
    boolean shows(Context context, ListedContainer container) {
      return switch (this) {
        case ALL -> true;
        case YES -> context.anyFollowed(container);
        case NO -> !context.everyPortFollowed(container);
      };
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

  /**
   * What the lines need to say who follows what. Each port already says it for its whole target.
   *
   * @param composeGroups every container of each Compose service, as {@code project/service}, in
   *     both states, as a Binding follows them all
   */
  private record Context(
      Workstation workstation, Map<String, List<ListedContainer>> composeGroups) {

    /** The Services that follow {@code port} of the target of {@code group}. */
    static List<String> followersAt(List<ListedContainer> group, int port) {
      return group.stream()
          .flatMap(member -> member.ports().stream())
          .filter(mapped -> mapped.containerPort() == port)
          .findFirst()
          .map(ListedContainer.ContainerPort::followedBy)
          .orElse(List.of());
    }

    /** The container, or every container of its Compose service. */
    List<ListedContainer> group(ListedContainer container) {
      if (container.composeProject() != null && container.composeService() != null) {
        return composeGroups.get(container.composeProject() + "/" + container.composeService());
      }
      return List.of(container);
    }

    boolean anyFollowed(ListedContainer container) {
      return group(container).stream().anyMatch(member -> !member.followedBy().isEmpty());
    }

    /** Followed, and at every port that is known. No port is known for a stopped container. */
    boolean everyPortFollowed(ListedContainer container) {
      List<ListedContainer> group = group(container);
      return anyFollowed(container)
          && group.stream()
              .flatMap(member -> member.containerPorts().stream())
              .distinct()
              .noneMatch(port -> followersAt(group, port).isEmpty());
    }
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
    Map<String, List<ListedContainer>> composeGroups = new TreeMap<>();
    for (ListedContainer container : ordered) {
      if (container.composeProject() != null && container.composeService() != null) {
        composeGroups
            .computeIfAbsent(
                container.composeProject() + "/" + container.composeService(),
                ignored -> new ArrayList<>())
            .add(container);
      }
    }
    Context context = new Context(workstation, composeGroups);
    List<ListedContainer> shown =
        ordered.stream()
            .filter(container -> filters.state().shows(container))
            .filter(container -> filters.followed().shows(context, container))
            .toList();
    region
        .div()
        .attrClass("container-list")
        .of(list -> {
          if (filters.state() != StateFilter.STOPPED) {
            stateSection(list, context, shown, filters, true);
          }
          if (filters.state() != StateFilter.RUNNING) {
            stateSection(list, context, shown, filters, false);
          }
        })
        .__();
  }

  private static void stateSection(
      FlowContent<?, ?> parent,
      Context context,
      List<ListedContainer> containers,
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
                    targetLines(
                        group,
                        context,
                        new Target(
                            service.getKey(),
                            target,
                            service.getValue(),
                            context.composeGroups().get(target)));
                  }
                })
                .__();
          }
          if (!standalone.isEmpty()) {
            section
                .div()
                .attrClass("container-project")
                .h3()
                .attrClass("container-project-heading")
                .text("Other containers")
                .__()
                .of(group -> {
                  for (ListedContainer container : standalone) {
                    List<ListedContainer> only = List.of(container);
                    targetLines(
                        group, context, new Target(container.name(), container.name(), only, only));
                  }
                })
                .__();
          }
        })
        .__();
  }

  /** {@code port} is none for a container with no known ports: the form asks for it. */
  private static void createLink(FlowContent<?, ?> parent, String target, Integer port) {
    parent
        .a()
        .attrClass("btn container-create")
        .addAttr("data-size", "sm")
        .addAttr("data-variant", "outline")
        .attrHref(
            Layout.attr(
                PATH
                    + "/services/new?target="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8)
                    + (port == null ? "" : "&port=" + port)))
        .addAttr(
            "aria-label",
            Layout.attr("Create Service for " + target + (port == null ? "" : " port " + port)))
        .text("Create Service")
        .__();
  }

  /**
   * What a Service can follow: a standalone container or a Compose service.
   *
   * @param heading what the first line calls it
   * @param name the target that the Create Service form takes
   * @param shown its containers in the state of the section
   * @param all its containers in both states, as a Binding follows them all
   */
  private record Target(
      String heading, String name, List<ListedContainer> shown, List<ListedContainer> all) {

    /** Docker lists no ports for a stopped container. */
    List<Integer> ports() {
      return shown.stream()
          .flatMap(container -> container.containerPorts().stream())
          .distinct()
          .sorted()
          .toList();
    }
  }

  /**
   * A line for each Container Port of the target, so each says which Services follow that port, or
   * offers to create one. A target with no known ports has one line, which says which Services
   * follow it at any port, and always offers the button, as the port is not known. A Compose
   * service with several containers shows only these lines: it hides its containers, and each port
   * shows only its number.
   */
  private static void targetLines(FlowContent<?, ?> parent, Context context, Target target) {
    boolean single = target.shown().size() == 1;
    ListedContainer only = target.shown().getFirst();
    List<Integer> ports = target.ports();
    Consumer<Div<?>> first =
        name -> {
          name.h4().text(target.heading()).__();
          if (!single) {
            name.span()
                .attrClass("container-detail")
                .text(target.shown().size() + " containers")
                .__();
            return;
          }
          if (!target.heading().equals(only.name())) {
            name.span().attrClass("container-detail").text(only.name()).__();
          }
          health(name, only);
        };
    if (ports.isEmpty()) {
      List<String> followedBy =
          target.all().stream()
              .flatMap(container -> container.followedBy().stream())
              .distinct()
              .sorted()
              .toList();
      line(
          parent,
          context,
          "container-line container-target",
          first,
          ContainersPage::noPorts,
          followedBy,
          target.name(),
          null);
    }
    for (int i = 0; i < ports.size(); i++) {
      int port = ports.get(i);
      List<String> followedBy = Context.followersAt(target.all(), port);
      line(
          parent,
          context,
          i == 0 ? "container-line container-target" : "container-line container-more-port",
          i == 0 ? first : name -> {},
          cell -> chips(cell, "Container Port", List.of(String.valueOf(port))),
          followedBy,
          followedBy.isEmpty() ? target.name() : null,
          port);
    }
  }

  private static void health(Div<?> name, ListedContainer container) {
    if (container.health() != null) {
      name.span()
          .attrClass("container-detail")
          .addAttr("title", "Healthcheck")
          .text(container.health().value())
          .__();
    }
  }

  /** A line has the name, the ports, who follows them, and the button when it has a target. */
  private static void line(
      FlowContent<?, ?> parent,
      Context context,
      String css,
      Consumer<Div<?>> name,
      Consumer<Div<?>> ports,
      Collection<String> followedBy,
      String createTarget,
      Integer port) {
    parent
        .div()
        .attrClass(css)
        .div()
        .attrClass("container-name")
        .of(name::accept)
        .__()
        .div()
        .of(ports::accept)
        .__()
        .div()
        .attrClass("container-follow")
        .of(cell -> followers(cell, context.workstation(), followedBy))
        .__()
        .of(line -> {
          if (createTarget != null) {
            createLink(line, createTarget, port);
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

  private static void noPorts(FlowContent<?, ?> parent) {
    parent.p().attrClass("container-no-ports").text("No known ports").__();
  }

  private static void chips(FlowContent<?, ?> parent, String label, List<String> texts) {
    parent
        .ul()
        .attrClass("container-ports")
        .addAttr("aria-label", Layout.attr(label))
        .of(list -> {
          for (String text : texts) {
            list.li().text(text).__();
          }
        })
        .__();
  }
}
