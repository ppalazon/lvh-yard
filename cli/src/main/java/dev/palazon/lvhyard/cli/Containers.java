package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "containers",
    mixinStandardHelpOptions = true,
    description = "Lists the running containers that LVH Yard does not follow yet.")
class Containers implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    List<Dashboard.UnfollowedContainer> containers = yard.dashboard().containers();
    PrintWriter out = spec.commandLine().getOut();
    if (containers.isEmpty()) {
      out.println("Every running container is followed.");
    }
    // The dashboard sends Compose projects first, so a new heading starts each group.
    String lastHeading = null;
    for (Dashboard.UnfollowedContainer container : containers) {
      String heading =
          container.composeProject() == null
              ? "Other containers"
              : "Compose project " + container.composeProject();
      if (!Objects.equals(heading, lastHeading)) {
        out.println(heading);
        lastHeading = heading;
      }
      out.println(
          "  "
              + container.name()
              + ": "
              + ports(container.ports())
              + (container.health() == null ? "" : "; " + container.health())
              + ". Imports as "
              + container.service()
              + ".");
    }
    out.flush();
  }

  private static String ports(List<Dashboard.ContainerPort> ports) {
    if (ports.isEmpty()) {
      return "no exposed port";
    }
    return (ports.size() == 1 ? "port " : "ports ")
        + ports.stream()
            .map(port ->
                port.hostPort() == null
                    ? port.containerPort() + " not published"
                    : port.containerPort() + " on host port " + port.hostPort())
            .collect(Collectors.joining(", "));
  }
}
