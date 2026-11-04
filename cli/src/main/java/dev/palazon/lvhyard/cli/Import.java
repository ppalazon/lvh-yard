package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "import",
    mixinStandardHelpOptions = true,
    description = "Binds a running container to a Service, and publishes a new Service.")
class Import implements Runnable {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<container>", description = "The running container's name.")
  String container;

  @Option(
      names = "--port",
      paramLabel = "<port>",
      description = "The port inside the container (default: its only port).")
  Integer port;

  @Option(
      names = "--name",
      paramLabel = "<service>",
      description = "The Service's name (default: the Compose service or the container name).")
  String name;

  @Option(names = "--no-publish", description = "Binds a new Service without publishing it.")
  boolean noPublish;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard.Service imported =
        yard.dashboard().importContainer(container, port, name, noPublish ? false : null);
    PrintWriter out = spec.commandLine().getOut();
    out.println(
        "Imported "
            + container
            + " as "
            + imported.name()
            + (imported.publication() == null
                ? ". " + imported.name() + " is not published."
                : ", published at " + imported.hostname() + "."));
    out.flush();
    PrintWriter err = spec.commandLine().getErr();
    imported.warnings().forEach(warning -> err.println("Warning: " + warning.message()));
    err.flush();
  }
}
