package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.services.AliasTarget;
import cc.lvhy.yard.dashboard.services.HealthCheck;
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
 * @param routeProfiles each Route Profile's definition, by name
 */
public record Backup(
    ExportedFrom exportedFrom,
    Map<String, Map<String, Object>> routeProfiles,
    Map<String, Service> services) {

  public static final int VERSION = 3;

  public Backup {
    routeProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(routeProfiles));
    services = Collections.unmodifiableMap(new LinkedHashMap<>(services));
  }

  /**
   * @param at {@code null} when the file leaves it out
   */
  public record ExportedFrom(String workstation, OffsetDateTime at) {}

  /**
   * @param publication {@code null} when the file has no {@code publication} key
   * @param instancePriority whether the Instance Priority is on; {@code null} when the file leaves
   *     it out
   * @param sources its Instance Sources in their order. Foreign Instances are never in a Backup.
   */
  public record Service(Publication publication, Boolean instancePriority, List<Source> sources) {

    public Service {
      sources = List.copyOf(sources);
    }

    public List<StaticInstance> staticInstances() {
      return only(StaticInstance.class);
    }

    public List<ContainerBinding> containerBindings() {
      return only(ContainerBinding.class);
    }

    public List<Alias> aliases() {
      return only(Alias.class);
    }

    private <T extends Source> List<T> only(Class<T> kind) {
      return sources.stream().filter(kind::isInstance).map(kind::cast).toList();
    }
  }

  /** One Instance Source of a Service. */
  public sealed interface Source permits StaticInstance, ContainerBinding, Alias {}

  /** {@code publication: none}, or a {@link Published} object. */
  public sealed interface Publication permits Unpublished, Published {}

  /** {@code publication: none}. */
  public record Unpublished() implements Publication {}

  /**
   * @param scope {@code null} when the file leaves it out
   * @param routeProfiles in the order the Route applies them; empty when the file leaves it out
   * @param accessLog false when the file leaves it out
   */
  public record Published(String scope, List<String> routeProfiles, boolean accessLog)
      implements Publication {

    public Published {
      routeProfiles = routeProfiles == null ? List.of() : List.copyOf(routeProfiles);
    }
  }

  /**
   * @param host {@code null} for the LAN address of the Workstation that reads the file
   * @param check {@code null} when the file leaves it out
   */
  public record StaticInstance(
      String host, int port, HealthCheck check, List<String> tags, Map<String, String> meta)
      implements Source {

    public StaticInstance {
      tags = tags == null ? List.of() : List.copyOf(tags);
      meta = meta == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }
  }

  /** It follows either {@code container}, or {@code composeProject} and {@code composeService}. */
  public record ContainerBinding(
      String container, String composeProject, String composeService, int port) implements Source {

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

  /** An Alias of a Service, to the Remote Service {@code service} of {@code workstation}. */
  public record Alias(String workstation, String service) implements Source {

    /** As the API names it: {@code <workstation>/<service>}. */
    public String id() {
      return new AliasTarget(workstation, service).id();
    }
  }
}
