package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.services.HealthCheck;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Backup file holds, in version {@value #VERSION} of its format. {@link BackupFormat} reads
 * and writes it. The maps keep the order of the file. Nothing here is checked against the rules of
 * the API: an import does that.
 *
 * @param exportedFrom {@code null} when the file leaves it out
 * @param routeProfiles each Route Profile's middleware in Traefik's format, by name
 */
public record Backup(
    ExportedFrom exportedFrom,
    Map<String, Map<String, Object>> routeProfiles,
    Map<String, Service> services,
    Map<String, Alias> aliases) {

  public static final int VERSION = 1;

  public Backup {
    routeProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(routeProfiles));
    services = Collections.unmodifiableMap(new LinkedHashMap<>(services));
    aliases = Collections.unmodifiableMap(new LinkedHashMap<>(aliases));
  }

  /**
   * @param at {@code null} when the file leaves it out
   */
  public record ExportedFrom(String workstation, OffsetDateTime at) {}

  /**
   * @param publication {@code null} when the file has no {@code publication} key
   */
  public record Service(
      Publication publication,
      List<StaticInstance> staticInstances,
      List<ContainerBinding> containerBindings) {

    public Service {
      staticInstances = List.copyOf(staticInstances);
      containerBindings = List.copyOf(containerBindings);
    }
  }

  /** {@code publication: none}, or a {@link Published} object. */
  public sealed interface Publication permits Unpublished, Published {}

  /** {@code publication: none}. */
  public record Unpublished() implements Publication {}

  /**
   * @param scope {@code null} when the file leaves it out
   * @param priority the Instance Priority kinds, empty for off; {@code null} when the file leaves
   *     it out
   * @param routeProfiles in the order the Route applies them; empty when the file leaves it out
   * @param accessLog false when the file leaves it out
   */
  public record Published(
      String scope, List<String> priority, List<String> routeProfiles, boolean accessLog)
      implements Publication {

    public Published {
      priority = priority == null ? null : List.copyOf(priority);
      routeProfiles = routeProfiles == null ? List.of() : List.copyOf(routeProfiles);
    }
  }

  /**
   * @param host {@code null} for the LAN address of the Workstation that reads the file
   * @param check {@code null} when the file leaves it out
   */
  public record StaticInstance(
      String host, int port, HealthCheck check, List<String> tags, Map<String, String> meta) {

    public StaticInstance {
      tags = tags == null ? List.of() : List.copyOf(tags);
      meta = meta == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }
  }

  /** It follows either {@code container}, or {@code composeProject} and {@code composeService}. */
  public record ContainerBinding(
      String container, String composeProject, String composeService, int port) {

    public boolean followsCompose() {
      return container == null;
    }

    /**
     * As the API names it: {@code <container>:<port>} or {@code
     * compose:<project>:<service>:<port>}.
     */
    public String id() {
      return followsCompose()
          ? "compose:" + composeProject + ":" + composeService + ":" + port
          : container + ":" + port;
    }
  }

  public record Alias(String workstation, String service) {}
}
