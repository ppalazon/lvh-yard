package dev.palazon.lvhyard.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code lvh-yard register}: a Static Instance of one of this Workstation's Services, or, with
 * {@code --container} or {@code --compose-project} and {@code --compose-service}, a Container
 * Binding.
 */
@Command(
    name = "register",
    mixinStandardHelpOptions = true,
    description = "Registers an Instance of a Service, or binds it to a container.")
class Register implements Runnable {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<service>", description = "The Service's name, a single DNS label.")
  String service;

  @Option(
      names = "--host",
      paramLabel = "<host>",
      description =
          "The address the Instance listens on (default: this Workstation's LAN address).")
  String host;

  @Option(
      names = "--port",
      paramLabel = "<port>",
      required = true,
      description = "The port the Instance listens on. For a container, the port inside it.")
  int port;

  @Option(
      names = "--container",
      paramLabel = "<name>",
      description =
          "Binds the Service to this container. While it runs, it is a Bound Instance with a TCP"
              + " check.")
  String container;

  @Option(
      names = "--compose-project",
      paramLabel = "<project>",
      description =
          "With --compose-service, binds the Service to a Compose service of this project. Each"
              + " running container is a Bound Instance.")
  String composeProject;

  @Option(
      names = "--compose-service",
      paramLabel = "<service>",
      description = "The Compose service to bind to, in --compose-project.")
  String composeService;

  @Option(
      names = "--check",
      paramLabel = "<type>",
      description =
          "The Health check: tcp connects to the port, http GETs --path (default: http with --path,"
              + " else tcp).")
  String check;

  @Option(
      names = "--path",
      paramLabel = "<path>",
      description = "The path an HTTP check GETs (default: /).")
  String path;

  @Option(
      names = "--interval",
      paramLabel = "<duration>",
      description = "Time between checks, 1s or more, such as 5s or 1m (default: 10s).")
  String interval;

  @Option(
      names = "--timeout",
      paramLabel = "<duration>",
      description = "How long the check waits for an answer, such as 500ms (default: 2s).")
  String timeout;

  @Option(
      names = "--tag",
      paramLabel = "<tag>",
      description = "A word that labels the Instance. Repeat for more.")
  List<String> tags = new ArrayList<>();

  @Option(
      names = "--meta",
      paramLabel = "<key=value>",
      description = "A note about the Instance. Repeat for more.")
  Map<String, String> meta = new LinkedHashMap<>();

  @Spec CommandSpec spec;

  @Override
  public void run() {
    if (container != null || composeProject != null || composeService != null) {
      bind();
      return;
    }
    Dashboard.Instance instance =
        yard.dashboard()
            .register(
                service,
                host,
                port,
                new Dashboard.Check(check, path, interval, timeout),
                tags,
                meta);
    spec.commandLine()
        .getOut()
        .println(
            "Registered "
                + service
                + " at "
                + instance.address()
                + ", Instance "
                + instance.id()
                + ".");
    spec.commandLine().getOut().flush();
  }

  private void bind() {
    boolean compose = composeProject != null || composeService != null;
    if (compose && container != null) {
      throw new YardException(
          "Give --container, or --compose-project and --compose-service, not both.");
    }
    if (compose && (composeProject == null || composeService == null)) {
      throw new YardException(
          "A Compose service needs both --compose-project and --compose-service.");
    }
    List<String> staticOnly = new ArrayList<>();
    if (host != null) {
      staticOnly.add("--host");
    }
    for (String[] option :
        new String[][] {
          {"--check", check}, {"--path", path}, {"--interval", interval}, {"--timeout", timeout}
        }) {
      if (option[1] != null) {
        staticOnly.add(option[0]);
      }
    }
    if (!tags.isEmpty()) {
      staticOnly.add("--tag");
    }
    if (!meta.isEmpty()) {
      staticOnly.add("--meta");
    }
    if (!staticOnly.isEmpty()) {
      String last = staticOnly.removeLast();
      throw new YardException(
          "A Container Binding takes only "
              + (compose ? "--compose-project, --compose-service" : "--container")
              + " and --port, not "
              + (staticOnly.isEmpty() ? "" : String.join(", ", staticOnly) + " or ")
              + last
              + ": those are for a Static Instance.");
    }
    Dashboard.ContainerBinding binding =
        compose
            ? yard.dashboard().bindCompose(service, composeProject, composeService, port)
            : yard.dashboard().bind(service, container, port);
    spec.commandLine()
        .getOut()
        .println(
            "Bound "
                + service
                + " to "
                + binding.follows()
                + ", port "
                + binding.port()
                + ". "
                + (compose ? composeInstances(binding) : containerInstance(binding)));
    spec.commandLine().getOut().flush();
  }

  /** Whether a container's Binding gives its Bound Instance now. */
  private String containerInstance(Dashboard.ContainerBinding binding) {
    return binding.instances().isEmpty()
        ? "No container of that name runs now: "
            + service
            + " gets its Bound Instance once one starts."
        : "It runs now, so " + service + " has its Bound Instance.";
  }

  /**
   * How many Bound Instances a Compose service's Binding gives now, one per container that runs.
   */
  private String composeInstances(Dashboard.ContainerBinding binding) {
    int running = binding.instances().size();
    if (running == 0) {
      return "None of its containers runs now: "
          + service
          + " gets a Bound Instance for each one that starts.";
    }
    return running
        + " of its containers "
        + (running == 1 ? "runs" : "run")
        + " now, so "
        + service
        + " has "
        + running
        + " Bound Instance"
        + (running == 1 ? "" : "s")
        + ".";
  }
}
