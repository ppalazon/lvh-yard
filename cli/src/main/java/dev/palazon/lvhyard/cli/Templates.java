package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The files {@code lvh-yard init} writes, from templates inside the CLI. {@code compose.yaml} and
 * {@code .env.example} are the repository's own, copied in by the build (cli/pom.xml), so they
 * cannot drift; {@code route-profiles.yaml} is the CLI's own. The native executable lists them in
 * {@code META-INF/native-image/dev.palazon/cli/reachability-metadata.json}.
 */
final class Templates {

  static final String COMPOSE = "compose.yaml";
  static final String ENV = ".env";
  static final String ROUTE_PROFILES = "route-profiles.yaml";

  /** The dashboard's image in the repository's {@code compose.yaml}, before its tag. */
  private static final String DASHBOARD_IMAGE = "ghcr.io/ppalazon/lvh-yard-dashboard:";

  private Templates() {}

  /**
   * {@code compose.yaml} for a Workstation folder: the dashboard image pinned to this CLI's
   * version, and no {@code build: .}, as the folder has no sources to build from.
   */
  static String compose() {
    String repository = read(COMPOSE);
    String latest = DASHBOARD_IMAGE + "latest}";
    String build = "    build: .\n";
    if (!repository.contains(latest) || !repository.contains(build)) {
      throw new IllegalStateException(
          "compose.yaml no longer has the dashboard image line or its build line that init edits");
    }
    return repository.replace(latest, DASHBOARD_IMAGE + dashboardTag() + "}").replace(build, "");
  }

  /** {@code .env.example}, with every setting and its comment, ready to be filled in. */
  static EnvFile env() {
    return EnvFile.parse(read(".env.example"));
  }

  /** {@code route-profiles.yaml}, with commented examples only. */
  static String routeProfiles() {
    return read(ROUTE_PROFILES);
  }

  /**
   * The dashboard image's tag that matches this CLI: its version, as a release tags the image, or
   * {@code latest} for a snapshot build, which has no image of its own.
   */
  static String dashboardTag() {
    return Version.VERSION.endsWith("-SNAPSHOT") ? "latest" : Version.VERSION;
  }

  private static String read(String name) {
    try (InputStream in = Templates.class.getResourceAsStream("/templates/" + name)) {
      if (in == null) {
        throw new IllegalStateException("The template " + name + " is missing from lvh-yard");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
