package dev.palazon.lvhyard.dashboard.status;

import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.DockerSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Whether the dashboard can reach this Workstation's Docker, which Container Bindings need, and its
 * version. When it cannot, it says whether the socket is missing or cannot be read, and how to fix
 * it.
 */
@Component
@Order(StatusOrder.DOCKER)
class DockerStatus implements StatusPart {

  static final String NAME = "Docker";

  private final Docker docker;

  private final DockerSettings settings;

  DockerStatus(Docker docker, DockerSettings settings) {
    this.docker = docker;
    this.settings = settings;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Part read() {
    Docker.Version version;
    try {
      version = docker.version();
    } catch (RuntimeException e) {
      return Part.failing(NAME, whyNot(e));
    }
    return Part.of(
        NAME,
        "Docker answers, so Container Bindings work.",
        List.of(
            Part.Detail.fact("version", version.version()),
            Part.Detail.fact("API version", version.apiVersion()),
            Part.Detail.fact("host", settings.host().toString())));
  }

  /** Why Docker did not answer: no socket, a socket the dashboard cannot read, or {@code e}. */
  private String whyNot(RuntimeException e) {
    if ("unix".equals(settings.host().getScheme())) {
      Path socket = Path.of(settings.host().getPath());
      if (!Files.exists(socket)) {
        return "There is no Docker socket at "
            + socket
            + ". Is Docker running, and is DOCKER_HOST right?";
      }
      if (!Files.isReadable(socket) || !Files.isWritable(socket)) {
        return "The dashboard cannot read "
            + socket
            + ": permission denied."
            + group(socket)
                .map(group ->
                    " Add the dashboard's user to the group "
                        + group
                        + "; for the dashboard's container, set YARD_DOCKER_GID in .env to that"
                        + " group's ID.")
                .orElse("");
      }
    }
    return "Docker does not answer at " + settings.host() + ": " + e.getMessage();
  }

  private static Optional<String> group(Path socket) {
    try {
      return Optional.of(Files.readAttributes(socket, PosixFileAttributes.class).group().getName());
    } catch (IOException | UnsupportedOperationException e) {
      return Optional.empty();
    }
  }
}
