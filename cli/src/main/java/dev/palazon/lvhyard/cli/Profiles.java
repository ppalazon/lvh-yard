package dev.palazon.lvhyard.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "profiles",
    mixinStandardHelpOptions = true,
    subcommands = {
      Profiles.Show.class,
      Profiles.Set.class,
      Profiles.Edit.class,
      Profiles.Remove.class
    },
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

  /** {@code edit} compares the edited text with this, so {@code show} must print the same. */
  private static String fileText(Dashboard.RouteProfileDetail profile) {
    return RouteProfileFile.write(Map.of(profile.name(), profile.definition()));
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new YardException("Cannot read " + file + ": " + e.getMessage());
    }
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
      out.print(fileText(profile));
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
            ? new String(profiles.yard.shell().input().readAllBytes(), StandardCharsets.UTF_8)
            : read(Path.of(file));
      } catch (IOException e) {
        throw new YardException("Cannot read the standard input: " + e.getMessage());
      }
    }
  }

  @Command(
      name = "edit",
      mixinStandardHelpOptions = true,
      description =
          "Opens a Route Profile in $EDITOR, else vi, and saves it after the editor closes.")
  static class Edit implements Runnable {

    @ParentCommand Profiles profiles;

    @Parameters(paramLabel = "<name>", description = "The Route Profile's name.")
    String name;

    @Spec CommandSpec spec;

    private BufferedReader answers;

    @Override
    public void run() {
      Dashboard dashboard = profiles.yard.dashboard();
      Dashboard.RouteProfileDetail profile = dashboard.routeProfile(name);
      String original = fileText(profile);
      Path file = newFile(original);
      PrintWriter out = spec.commandLine().getOut();
      while (true) {
        try {
          edit(file);
        } catch (YardException editorFailed) {
          if (read(file).equals(original)) {
            delete(file);
            throw editorFailed;
          }
          throw new YardException(editorFailed.getMessage() + " Your text is in " + file + ".");
        }
        String text = read(file);
        if (text.equals(original)) {
          out.println("No change to the Route Profile " + name + ".");
          break;
        }
        try {
          dashboard.putRouteProfile(name, middleware(text));
          out.println("Saved the Route Profile " + name + ".");
          break;
        } catch (YardException refused) {
          if (!editAgain(refused.getMessage())) {
            throw new YardException(
                "Did not save the Route Profile " + name + ". Your text is in " + file + ".");
          }
        }
      }
      out.flush();
      delete(file);
    }

    /** The one profile of {@code text}, which must be the one named {@code name}. */
    private Object middleware(String text) {
      Map<String, Object> set = RouteProfileFile.read(text);
      if (!set.keySet().equals(java.util.Set.of(name))) {
        throw new YardException(
            "The file must hold only the Route Profile "
                + name
                + ". It holds "
                + String.join(", ", set.keySet())
                + ".");
      }
      return set.get(name);
    }

    /** Enter means yes. The end of the input means no. */
    private boolean editAgain(String reason) {
      PrintWriter err = spec.commandLine().getErr();
      err.println(reason);
      err.print("Edit again? [Y/n] ");
      err.flush();
      try {
        if (answers == null) {
          answers =
              new BufferedReader(
                  new InputStreamReader(profiles.yard.shell().input(), StandardCharsets.UTF_8));
        }
        String answer = answers.readLine();
        if (answer == null) {
          err.println();
          return false;
        }
        answer = answer.strip().toLowerCase(Locale.ROOT);
        return answer.isEmpty() || answer.equals("y") || answer.equals("yes");
      } catch (IOException e) {
        return false;
      }
    }

    private void edit(Path file) {
      String editor = profiles.yard.shell().environment().get("EDITOR");
      if (editor == null || editor.isBlank()) {
        editor = "vi";
      }
      try {
        int exitCode = profiles.yard.shell().edit(editor, file);
        if (exitCode != 0) {
          throw new YardException(
              "The editor ended with exit code " + exitCode + ", so nothing was saved.");
        }
      } catch (IOException e) {
        throw new YardException("Cannot start the editor " + editor + ": " + e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new YardException("Stopped while waiting for the editor.");
      }
    }

    /** Readable by its owner only, as a basicAuth profile holds password hashes. */
    private static Path newFile(String text) {
      try {
        Path file = Files.createTempFile("route-profile-", ".yaml");
        Files.writeString(file, text);
        return file;
      } catch (IOException e) {
        throw new YardException("Cannot write a temporary file: " + e.getMessage());
      }
    }

    private static void delete(Path file) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException e) {
        // A file in the temporary folder that the system removes later.
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
