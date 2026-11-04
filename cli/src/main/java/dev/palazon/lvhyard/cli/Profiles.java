package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "profiles",
    mixinStandardHelpOptions = true,
    description = "Lists the Route Profiles of route-profiles.yaml, with their types and errors.")
class Profiles implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard.RouteProfiles file = yard.dashboard().routeProfiles();
    PrintWriter out = spec.commandLine().getOut();
    if (file.profiles().isEmpty()) {
      out.println("No Route Profiles. Write them in route-profiles.yaml, next to compose.yaml.");
    }
    for (Dashboard.RouteProfile profile : file.profiles()) {
      StringBuilder line = new StringBuilder(profile.name()).append(": ");
      if (profile.type() != null) {
        line.append(profile.type());
      }
      if (profile.error() != null) {
        line.append(profile.type() == null ? "refused. " : ", refused. ").append(profile.error());
      }
      out.println(line);
    }
    out.flush();
  }
}
