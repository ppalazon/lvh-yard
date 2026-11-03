package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "Lists the Services of every Workstation.")
class ListServices implements Runnable {

  @ParentCommand Yard yard;

  @Option(
      names = "--workstation",
      paramLabel = "<name>",
      description = "List only the Services of Workstation <name>.")
  String workstation;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard dashboard = yard.dashboard();
    PrintWriter out = spec.commandLine().getOut();
    if (workstation == null) {
      Dashboard.Yard yard = dashboard.yard();
      print(out, yard.home().name() + " (this Workstation)", yard.home().services());
      for (Dashboard.YardWorkstation remote : yard.remotes()) {
        if (remote.unreachable()) {
          out.println(remote.name() + ": Workstation unreachable");
        } else {
          print(out, remote.name(), remote.services());
        }
      }
    } else {
      print(out, workstation, dashboard.services(workstation));
    }
    out.flush();
  }

  private static void print(PrintWriter out, String name, List<Dashboard.Service> services) {
    if (services.isEmpty()) {
      out.println(name + ": no Services");
      return;
    }
    out.println(name);
    for (Dashboard.Service service : services) {
      out.println(
          "  "
              + service.name()
              + ": "
              + service.state()
              + (service.hostname() == null ? "" : " at " + service.hostname())
              + (service.isCore() ? "  (Core Service)" : ""));
      for (Dashboard.Instance instance : service.instances()) {
        out.printf(
            "    %-13s  %-8s  %s%s%n",
            instance.address(),
            instance.health(),
            instance.id(),
            instance.outOfRange()
                ? "  out of range: only " + service.workstation() + " can reach it, no Route"
                : "");
      }
    }
  }
}
