package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Unmatched;

/**
 * Has no options of its own, so {@code --help} and every other word reach Docker. {@link
 * Yard#commandLine()} makes picocli stop at the first word.
 */
@Command(
    name = "compose",
    description = "Runs docker compose with the given arguments in this Workstation's folder.")
class Compose implements Callable<Integer> {

  @Unmatched List<String> arguments = new ArrayList<>();

  @Override
  public Integer call() {
    Path folder = ConfigFolder.forReading(System.getenv());
    if (!Files.exists(folder.resolve(Templates.COMPOSE))) {
      throw new YardException(
          "No " + Templates.COMPOSE + " in " + folder + ": run lvh-yard init first.");
    }
    List<String> command = new ArrayList<>(List.of("docker", "compose"));
    command.addAll(arguments);
    try {
      return new ProcessBuilder(command).directory(folder.toFile()).inheritIO().start().waitFor();
    } catch (IOException e) {
      throw new YardException("Could not run docker: " + e.getMessage() + ".");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return 130;
    }
  }
}
