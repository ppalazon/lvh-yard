package dev.palazon.lvhyard.dashboard.docker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Why Docker failed, in words that say what to do about it. */
@Component
public class DockerFailures {

  private final DockerSettings settings;

  DockerFailures(DockerSettings settings) {
    this.settings = settings;
  }

  /** {@code e} is what a {@link Docker} call threw. */
  public String reason(RuntimeException e) {
    if ("unix".equals(settings.host().getScheme())) {
      Path socket = Path.of(settings.host().getPath());
      if (!Files.exists(socket)) {
        return "No Docker socket at " + socket + ". Start Docker, or fix DOCKER_HOST.";
      }
      if (!Files.isReadable(socket) || !Files.isWritable(socket)) {
        return "The dashboard cannot read "
            + socket
            + ": permission denied."
            + group(socket)
                .map(group ->
                    " Add the dashboard's user to the group "
                        + group
                        + ", or set YARD_DOCKER_GID in .env to that group's ID.")
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
