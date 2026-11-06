package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "catalog",
    mixinStandardHelpOptions = true,
    description = "Lists the Services of every Workstation, one line each.")
class Catalog implements Runnable {

  @ParentCommand Yard yard;

  @Option(
      names = "--workstation",
      paramLabel = "<name>",
      description = "List only the Services of Workstation <name>.")
  String workstation;

  @Option(
      names = "--state",
      paramLabel = "<state>",
      description = "List only Services in this state: ${COMPLETION-CANDIDATES}.")
  State state;

  @Option(
      names = "--details",
      description = "Show Instance addresses, Health, IDs and Service Warnings.")
  boolean details;

  enum State {
    unpublished,
    unavailable,
    unhealthy,
    degraded,
    healthy
  }

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard.Yard snapshot = yard.dashboard().yard();
    List<Dashboard.YardWorkstation> workstations = new ArrayList<>();
    workstations.add(
        new Dashboard.YardWorkstation(snapshot.home().name(), false, snapshot.home().services()));
    workstations.addAll(snapshot.remotes());
    workstations.removeIf(item -> workstation != null && !workstation.equals(item.name()));
    if (workstations.isEmpty()) {
      throw new YardException("The Yard has no Workstation " + workstation + ".");
    }
    if (state != null) {
      workstations.replaceAll(item ->
          new Dashboard.YardWorkstation(
              item.name(),
              item.unreachable(),
              item.services().stream()
                  .filter(service -> state.name().equals(service.state()))
                  .toList()));
    }
    String format = null;
    if (!details) {
      int nameWidth = 1;
      int stateWidth = 1;
      int hostnameWidth = 1;
      for (Dashboard.YardWorkstation workstation : workstations) {
        for (Dashboard.Service service : workstation.services()) {
          nameWidth = Math.max(nameWidth, service.name().length());
          stateWidth = Math.max(stateWidth, service.state().length());
          hostnameWidth = Math.max(hostnameWidth, hostname(service).length());
        }
      }
      format = "  %-" + nameWidth + "s  %-" + stateWidth + "s  %-" + hostnameWidth + "s  %s%n";
    }
    PrintWriter out = spec.commandLine().getOut();
    for (Dashboard.YardWorkstation workstation : workstations) {
      String heading =
          workstation.name()
              + (workstation.name().equals(snapshot.home().name()) ? " (this Workstation)" : "");
      if (workstation.unreachable()) {
        out.println(workstation.name() + ": Workstation unreachable");
      } else if (workstation.services().isEmpty()) {
        out.println(heading + ": no Services");
      } else {
        out.println(heading);
        for (Dashboard.Service service : workstation.services()) {
          if (details) {
            printDetails(out, service);
            continue;
          }
          String instances =
              service.instances().isEmpty()
                  ? "No Instances"
                  : service.instances().stream()
                          .filter(instance -> "passing".equals(instance.health()))
                          .count()
                      + " of "
                      + service.instances().size()
                      + " passing";
          out.printf(format, service.name(), service.state(), hostname(service), instances);
        }
      }
    }
    out.flush();
  }

  private static void printDetails(PrintWriter out, Dashboard.Service service) {
    out.println(
        "  "
            + service.name()
            + ": "
            + service.state()
            + (service.hostname() == null ? "" : " at " + service.hostname()));
    for (Dashboard.Instance instance : service.instances()) {
      out.printf(
          "    %-13s  %-8s  %s%s%s%n",
          instance.address(),
          instance.health(),
          instance.id(),
          service.routes(instance) ? "  routed" : "",
          instance.outOfRange() ? "  out of range" : "");
    }
    for (Dashboard.Warning warning : service.warnings()) {
      out.println("    " + warning.line());
    }
  }

  private static String hostname(Dashboard.Service service) {
    return service.hostname() == null ? "" : service.hostname();
  }
}
