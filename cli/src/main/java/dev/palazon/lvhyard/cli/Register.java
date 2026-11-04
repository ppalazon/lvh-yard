package dev.palazon.lvhyard.cli;

import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Without a subcommand, it still takes a Service name and the options of a Static Instance or a
 * Container Binding, until {@code register container} replaces that form.
 */
@Command(
    name = "register",
    mixinStandardHelpOptions = true,
    subcommands = RegisterStatic.class,
    description = "Registers an Instance of a Service, or binds it to a container.")
class Register implements Runnable {

  @ParentCommand Yard yard;

  // Optional here, so that picocli lets a subcommand through. run() asks for them.
  @Parameters(
      arity = "0..1",
      paramLabel = "<service>",
      description = "The Service's name, a single DNS label.")
  String service;

  @Option(
      names = "--port",
      paramLabel = "<port>",
      description = "The port the Instance listens on. For a container, the port inside it.")
  Integer port;

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

  @Mixin StaticInstanceOptions options;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    if (service == null || port == null) {
      throw new ParameterException(
          spec.commandLine(),
          service == null
              ? "Missing required parameter: '<service>'"
              : "Missing required option: '--port=<port>'");
    }
    if (container != null || composeProject != null || composeService != null) {
      bind();
      return;
    }
    Dashboard.Instance instance =
        yard.dashboard()
            .register(
                service,
                options.host,
                port,
                options.healthCheck(),
                options.tags,
                options.meta,
                null);
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
    List<String> staticOnly = options.given();
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

  private String containerInstance(Dashboard.ContainerBinding binding) {
    return binding.instances().isEmpty()
        ? "No container of that name runs now: "
            + service
            + " gets its Bound Instance once one starts."
        : "It runs now, so " + service + " has its Bound Instance.";
  }

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
