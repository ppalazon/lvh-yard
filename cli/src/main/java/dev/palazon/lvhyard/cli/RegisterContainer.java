package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "container",
    mixinStandardHelpOptions = true,
    description = "Binds a named container to an existing Service.")
class RegisterContainer implements Runnable {

  @ParentCommand Register register;

  @Parameters(
      paramLabel = "<container>",
      description = "The container's name. It does not need to run.")
  String container;

  @Option(
      names = "--port",
      paramLabel = "<port>",
      description =
          "The port inside the container. Needed if it does not run or has more than one port.")
  Integer port;

  @Option(
      names = "--service",
      paramLabel = "<service>",
      description = "The Service's name (default: the container name).")
  String service;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard dashboard = register.dashboard();
    String name = service != null ? service : RegisterCompose.cleaned(container);
    dashboard.bindContainer(name, container, port != null ? port : onlyPort(dashboard));
    Dashboard.Service registered = dashboard.ownService(name);
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
    registered.warnings().forEach(warning -> err.println(warning.line()));
    err.flush();
  }

  /** The one Container Port that the dashboard lists for the container. */
  private int onlyPort(Dashboard dashboard) {
    List<Integer> ports =
        dashboard.containers().stream()
            .filter(listed -> listed.name().equals(container))
            .findFirst()
            .map(Dashboard.Container::containerPorts)
            .orElse(List.of());
    if (ports.size() == 1) {
      return ports.getFirst();
    }
    throw new YardException(
        ports.isEmpty()
            ? "No port is known for the container " + container + ": give --port."
            : "The container "
                + container
                + " has more than one port: give --port with one of "
                + ports.stream().map(String::valueOf).collect(Collectors.joining(", "))
                + ".");
  }
}
