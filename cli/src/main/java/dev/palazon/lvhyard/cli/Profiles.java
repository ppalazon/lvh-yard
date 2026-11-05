package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "profiles",
    mixinStandardHelpOptions = true,
    description = "Lists the Route Profiles of this Workstation.")
class Profiles implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    List<Dashboard.RouteProfile> profiles = yard.dashboard().routeProfiles();
    PrintWriter out = spec.commandLine().getOut();
    if (profiles.isEmpty()) {
      out.println("No Route Profiles.");
    }
    for (Dashboard.RouteProfile profile : profiles) {
      StringBuilder line = new StringBuilder(profile.name()).append(": ");
      if (profile.type() != null) {
        line.append(profile.type()).append(", ");
      }
      line.append(
          profile.services().isEmpty()
              ? "not used"
              : "used by " + String.join(", ", profile.services()));
      out.println(line);
    }
    out.flush();
  }
}
