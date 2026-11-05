package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "profiles",
    mixinStandardHelpOptions = true,
    subcommands = {Profiles.Show.class, Profiles.Set.class, Profiles.Remove.class},
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

  @Command(
      name = "show",
      mixinStandardHelpOptions = true,
      description = "Prints a Route Profile in Traefik's file format.")
  static class Show implements Runnable {

    @ParentCommand Profiles profiles;

    @Parameters(paramLabel = "<name>", description = "The Route Profile's name.")
    String name;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      Dashboard.RouteProfileDetail profile = profiles.yard.dashboard().routeProfile(name);
      PrintWriter out = spec.commandLine().getOut();
      out.print(RouteProfileFile.write(Map.of(profile.name(), profile.definition())));
      out.flush();
    }
  }

  @Command(
      name = "set",
      mixinStandardHelpOptions = true,
      description = "Adds or replaces the Route Profiles of a file in Traefik's format.")
  static class Set implements Runnable {

    @ParentCommand Profiles profiles;

    @Parameters(paramLabel = "<file>", description = "The file, or - for standard input.")
    String file;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      Map<String, Object> set = RouteProfileFile.read(text());
      profiles.yard.dashboard().putRouteProfiles(set);
      PrintWriter out = spec.commandLine().getOut();
      out.println(
          (set.size() == 1 ? "Saved the Route Profile " : "Saved the Route Profiles ")
              + String.join(", ", set.keySet())
              + ".");
      out.flush();
    }

    private String text() {
      try {
        return file.equals("-")
            ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
            : Files.readString(Path.of(file));
      } catch (IOException e) {
        throw new YardException("Cannot read " + file + ": " + e.getMessage());
      }
    }
  }

  @Command(
      name = "remove",
      mixinStandardHelpOptions = true,
      description = "Removes a Route Profile.")
  static class Remove implements Runnable {

    @ParentCommand Profiles profiles;

    @Parameters(paramLabel = "<name>", description = "The Route Profile's name.")
    String name;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      profiles.yard.dashboard().removeRouteProfile(name);
      spec.commandLine().getOut().println("Removed the Route Profile " + name + ".");
      spec.commandLine().getOut().flush();
    }
  }
}
