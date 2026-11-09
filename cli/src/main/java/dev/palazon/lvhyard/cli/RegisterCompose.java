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
    name = "compose",
    mixinStandardHelpOptions = true,
    description = "Binds a Compose service to an existing Service.")
class RegisterCompose implements Runnable {

  /** The longest DNS label, as the dashboard's Service name rule allows. */
  private static final int MAX_NAME_LENGTH = 63;

  @ParentCommand Register register;

  @Parameters(
      paramLabel = "<project>/<service>",
      description = "The Compose project and service. It does not need to run.")
  String compose;

  @Option(
      names = "--port",
      required = true,
      paramLabel = "<port>",
      description = "The port inside each container.")
  int port;

  @Option(
      names = "--service",
      paramLabel = "<service>",
      description = "The Service's name (default: the Compose service name).")
  String service;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    String[] projectAndService = compose.split("/", -1);
    if (projectAndService.length != 2
        || projectAndService[0].isBlank()
        || projectAndService[1].isBlank()) {
      throw new YardException("Give <project>/<service>, such as shop/web, not " + compose + ".");
    }
    String composeService = projectAndService[1];
    String name = service != null ? service : cleaned(composeService);
    Dashboard.ContainerBinding binding =
        register.dashboard().bindCompose(name, projectAndService[0], composeService, port);
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
  static String cleaned(String name) {
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
