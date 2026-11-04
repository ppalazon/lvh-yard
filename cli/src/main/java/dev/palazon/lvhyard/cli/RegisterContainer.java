package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.Locale;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "container",
    mixinStandardHelpOptions = true,
    description =
        "Binds a Service to a container or a Compose service, and publishes a new Service.")
class RegisterContainer implements Runnable {

  /** The longest DNS label, as the dashboard's Service name rule allows. */
  private static final int MAX_NAME_LENGTH = 63;

  @ParentCommand Register register;

  // Optional, as --compose takes its place.
  @Parameters(
      arity = "0..1",
      paramLabel = "<container>",
      description = "The container's name. It does not need to run.")
  String container;

  @Option(
      names = "--compose",
      paramLabel = "<project>/<service>",
      description =
          "Binds a Compose service instead of a container, such as shop/web. Each of its running"
              + " containers is an Instance. It does not need to run.")
  String compose;

  @Option(
      names = "--port",
      paramLabel = "<port>",
      description =
          "The port inside the container. Needed for --compose, and if the container does not run"
              + " or has more than one port.")
  Integer port;

  @Option(
      names = "--service",
      paramLabel = "<service>",
      description = "The Service's name (default: the Compose service or the container name).")
  String service;

  @Option(names = "--no-publish", description = "Binds a new Service without publishing it.")
  boolean noPublish;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    if (compose != null && container != null) {
      throw new YardException("Give a container or --compose, not both.");
    }
    if (compose != null) {
      bindCompose();
      return;
    }
    if (container == null) {
      throw new YardException("Give a container, or --compose <project>/<service>.");
    }
    Dashboard.Service registered =
        register
            .yard
            .dashboard()
            .registerContainer(container, port, service, noPublish ? false : null);
    PrintWriter out = spec.commandLine().getOut();
    out.println(
        "Registered "
            + container
            + " as "
            + registered.name()
            + (registered.publication() == null
                ? ". " + registered.name() + " is not published."
                : ", published at " + registered.hostname() + ".")
            + (registered.instances().isEmpty()
                ? " No container of that name runs now: "
                    + registered.name()
                    + " gets its Bound Instance once one starts."
                : ""));
    out.flush();
    PrintWriter err = spec.commandLine().getErr();
    registered.warnings().forEach(warning -> err.println("Warning: " + warning.message()));
    err.flush();
  }

  private void bindCompose() {
    String[] projectAndService = compose.split("/", -1);
    if (projectAndService.length != 2
        || projectAndService[0].isBlank()
        || projectAndService[1].isBlank()) {
      throw new YardException(
          "Give --compose as <project>/<service>, such as shop/web, not " + compose + ".");
    }
    if (port == null) {
      throw new YardException("A Compose service needs --port.");
    }
    String composeService = projectAndService[1];
    String name = service != null ? service : cleaned(composeService);
    Dashboard.ContainerBinding binding =
        register
            .yard
            .dashboard()
            .bindCompose(
                name, projectAndService[0], composeService, port, noPublish ? false : null);
    PrintWriter out = spec.commandLine().getOut();
    out.println(
        "Bound "
            + name
            + " to "
            + binding.follows()
            + ", port "
            + binding.port()
            + ". "
            + instances(name, binding.instances().size()));
    out.flush();
  }

  /** As the dashboard cleans a Compose service into the Service name it proposes. */
  private static String cleaned(String name) {
    String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    if (cleaned.length() > MAX_NAME_LENGTH) {
      cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
    }
    return cleaned.replaceAll("^-+|-+$", "");
  }

  private static String instances(String service, int running) {
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
