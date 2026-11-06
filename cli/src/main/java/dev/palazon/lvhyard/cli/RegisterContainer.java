package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "container",
    mixinStandardHelpOptions = true,
    description = "Binds a named container to a Service.")
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

  @Option(names = "--no-publish", description = "Binds a new Service without publishing it.")
  boolean noPublish;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard.Service registered =
        register
            .dashboard()
            .registerContainer(container, port, service, Register.publish(noPublish));
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
}
