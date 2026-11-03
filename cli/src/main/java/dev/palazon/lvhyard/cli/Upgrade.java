package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code lvh-yard init --upgrade}: moves the images of {@link #UPGRADED} in a folder's {@code
 * compose.yaml} to the template's, by line ({@link ComposeImages}), and changes nothing else.
 */
final class Upgrade {

  /** The services whose images {@code --upgrade} moves to this version's. */
  private static final List<String> UPGRADED = List.of("consul", "traefik", "dashboard");

  private Upgrade() {}

  /**
   * Upgrades {@code folder}'s {@code compose.yaml}, telling {@code out} what moved. When one of
   * {@link #UPGRADED} has no {@code image:} line, the file is left as it is.
   */
  static void run(Path folder, PrintWriter out) {
    Path file = folder.resolve(Templates.COMPOSE);
    String text;
    try {
      text = Files.readString(file);
    } catch (NoSuchFileException e) {
      throw new YardException(
          file + " does not exist: nothing to upgrade. Write it first with lvh-yard init.");
    } catch (IOException e) {
      throw new YardException("Could not read " + file + ": " + e.getMessage());
    }
    ComposeImages compose = ComposeImages.parse(text);
    List<String> missing = compose.missing(UPGRADED);
    if (!missing.isEmpty()) {
      throw new YardException(
          file
              + " has no image: line for "
              + String.join(", ", missing)
              + ": nothing was changed. Change its images by hand.");
    }
    ComposeImages template = ComposeImages.parse(Templates.compose());
    for (String service : UPGRADED) {
      String from = compose.image(service).orElseThrow();
      String to = template.image(service).orElseThrow();
      if (from.equals(to)) {
        out.println(service + ": " + to + ", already this version's");
      } else {
        compose.setImage(service, to);
        out.println(service + ": " + from + " -> " + to);
      }
    }
    try {
      Files.writeString(file, compose.text());
    } catch (IOException e) {
      throw new YardException("Could not write " + file + ": " + e.getMessage());
    }
    out.println(
        "Upgraded "
            + file
            + "; .env and route-profiles.yaml are untouched. Start the new images: cd "
            + folder
            + " && docker compose up -d");
    out.flush();
  }
}
