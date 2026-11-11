package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.backups.Backup.Alias;
import cc.lvhy.yard.dashboard.backups.Backup.ContainerBinding;
import cc.lvhy.yard.dashboard.backups.Backup.ExportedFrom;
import cc.lvhy.yard.dashboard.backups.Backup.Publication;
import cc.lvhy.yard.dashboard.backups.Backup.Published;
import cc.lvhy.yard.dashboard.backups.Backup.Service;
import cc.lvhy.yard.dashboard.backups.Backup.Source;
import cc.lvhy.yard.dashboard.backups.Backup.StaticInstance;
import cc.lvhy.yard.dashboard.backups.Backup.Unpublished;
import cc.lvhy.yard.dashboard.services.HealthCheck;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

/**
 * The YAML of a {@link Backup}. Reading checks only the shape of the file: its version, its keys
 * and the types of their values. An unknown key is refused at every level, so a typo is never
 * ignored.
 */
public final class BackupFormat {

  private static final String NONE = "none";

  private static final String STATIC_INSTANCE = "static-instance";

  private static final String CONTAINER_BINDING = "container-binding";

  private static final String ALIAS = "alias";

  private BackupFormat() {}

  public static String write(Backup backup) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("version", Backup.VERSION);
    if (backup.exportedFrom() != null) {
      Map<String, Object> from = new LinkedHashMap<>();
      from.put("workstation", backup.exportedFrom().workstation());
      if (backup.exportedFrom().at() != null) {
        from.put("at", backup.exportedFrom().at().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
      }
      root.put("exported-from", from);
    }
    root.put("route-profiles", backup.routeProfiles());
    Map<String, Object> services = new LinkedHashMap<>();
    backup.services().forEach((name, service) -> services.put(name, service(service)));
    root.put("services", services);
    return yaml().dump(root);
  }

  private static Map<String, Object> service(Service service) {
    Map<String, Object> entry = new LinkedHashMap<>();
    switch (service.publication()) {
      case null -> {}
      case Unpublished unpublished -> entry.put("publication", NONE);
      case Published published -> {
        Map<String, Object> publication = new LinkedHashMap<>();
        putIfPresent(publication, "scope", published.scope());
        if (!published.routeProfiles().isEmpty()) {
          publication.put("route-profiles", published.routeProfiles());
        }
        if (published.accessLog()) {
          publication.put("access-log", true);
        }
        entry.put("publication", publication);
      }
    }
    if (service.instancePriority() != null) {
      entry.put("instance-priority", service.instancePriority());
    }
    if (!service.sources().isEmpty()) {
      entry.put("sources", service.sources().stream().map(BackupFormat::source).toList());
    }
    return entry;
  }

  private static Map<String, Object> source(Source source) {
    Map<String, Object> entry = new LinkedHashMap<>();
    switch (source) {
      case StaticInstance instance -> entry.put(STATIC_INSTANCE, staticInstance(instance));
      case ContainerBinding binding -> entry.put(CONTAINER_BINDING, containerBinding(binding));
      case Alias alias ->
          entry.put(ALIAS, ordered("workstation", alias.workstation(), "service", alias.service()));
    }
    return entry;
  }

  private static Map<String, Object> staticInstance(StaticInstance instance) {
    Map<String, Object> entry = new LinkedHashMap<>();
    putIfPresent(entry, "host", instance.host());
    entry.put("port", instance.port());
    if (instance.check() != null) {
      Map<String, Object> check = new LinkedHashMap<>();
      putIfPresent(check, "type", instance.check().type());
      putIfPresent(check, "path", instance.check().path());
      putIfPresent(check, "interval", instance.check().interval());
      putIfPresent(check, "timeout", instance.check().timeout());
      entry.put("check", check);
    }
    if (!instance.tags().isEmpty()) {
      entry.put("tags", instance.tags());
    }
    if (!instance.meta().isEmpty()) {
      entry.put("meta", instance.meta());
    }
    return entry;
  }

  private static Map<String, Object> containerBinding(ContainerBinding binding) {
    Map<String, Object> entry = new LinkedHashMap<>();
    if (binding.followsCompose()) {
      entry.put(
          "compose",
          ordered("project", binding.composeProject(), "service", binding.composeService()));
    } else {
      entry.put("container", binding.container());
    }
    entry.put("port", binding.port());
    return entry;
  }

  private static Map<String, Object> ordered(
      String key1, Object value1, String key2, Object value2) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  private static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  /**
   * @throws RefusedBackupException with every problem of its shape, each with its place
   */
  public static Backup read(String text) {
    Object root;
    try {
      root = yaml().load(text);
    } catch (YAMLException e) {
      throw refused("", "The file is not valid YAML: " + e.getMessage());
    }
    Reader reader = new Reader();
    Backup backup = reader.backup(root);
    if (!reader.problems.isEmpty()) {
      throw new RefusedBackupException(reader.problems);
    }
    return backup;
  }

  private static String versionProblem(Object version) {
    if (version == null) {
      return "The file has no version. A Backup has version: " + Backup.VERSION + ".";
    }
    if (version instanceof Integer older && older < Backup.VERSION) {
      return "The version "
          + older
          + " is an older Backup format. This dashboard reads only version "
          + Backup.VERSION
          + ".";
    }
    return "The version "
        + version
        + " is not one this dashboard reads. It reads version "
        + Backup.VERSION
        + ".";
  }

  private static RefusedBackupException refused(String place, String reason) {
    return new RefusedBackupException(List.of(new BackupProblem(place, reason)));
  }

  /** Block style, as people write YAML by hand. */
  private static Yaml yaml() {
    DumperOptions dump = new DumperOptions();
    dump.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dump.setIndicatorIndent(2);
    dump.setIndentWithIndicator(true);
    return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(dump), dump);
  }

  /** Walks the tree that SnakeYAML gives, and collects a problem for each wrong value. */
  private static final class Reader {

    private final List<BackupProblem> problems = new ArrayList<>();

    Backup backup(Object root) {
      if (!(root instanceof Map<?, ?>)) {
        problem(
            "", "The file must be a Backup: a YAML mapping with version: " + Backup.VERSION + ".");
        return null;
      }
      Map<String, Object> file =
          keys(root, "", Set.of("version", "exported-from", "route-profiles", "services"));
      Object version = file.get("version");
      if (!Integer.valueOf(Backup.VERSION).equals(version)) {
        problem("version", versionProblem(version));
        return null;
      }
      return new Backup(
          exportedFrom(file.get("exported-from")),
          entries(file.get("route-profiles"), "route-profiles", this::middleware),
          entries(file.get("services"), "services", this::service));
    }

    private ExportedFrom exportedFrom(Object value) {
      if (value == null) {
        return null;
      }
      Map<String, Object> from = keys(value, "exported-from", Set.of("workstation", "at"));
      Object at = from.get("at");
      OffsetDateTime time = null;
      if (at instanceof Date date) {
        time = date.toInstant().atOffset(ZoneOffset.UTC);
      } else if (at != null) {
        try {
          time = OffsetDateTime.parse(at.toString());
        } catch (DateTimeParseException e) {
          problem("exported-from.at", "The time " + at + " is not one such as 2026-11-05T10:00Z.");
        }
      }
      return new ExportedFrom(text(from.get("workstation"), "exported-from.workstation"), time);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> middleware(Object value, String place) {
      if (!(value instanceof Map<?, ?> map)) {
        problem(place, "A Route Profile must map a Traefik middleware type to its options.");
        return Map.of();
      }
      return (Map<String, Object>) map;
    }

    private Service service(Object value, String place) {
      if (value == null) {
        return new Service(null, null, List.of());
      }
      Map<String, Object> entry =
          keys(value, place, Set.of("publication", "instance-priority", "sources"));
      Object priority = entry.get("instance-priority");
      return new Service(
          publication(entry.get("publication"), place + ".publication"),
          priority == null ? null : flag(priority, place + ".instance-priority", true),
          list(entry.get("sources"), place + ".sources", this::source).stream()
              .filter(Objects::nonNull)
              .toList());
    }

    /** {@code null} when it is not one mapping of one known kind, with a problem. */
    private Source source(Object value, String place) {
      if (!(value instanceof Map<?, ?> map) || map.size() != 1) {
        problem(
            place,
            "An Instance Source is a mapping with one key: static-instance, container-binding or"
                + " alias.");
        return null;
      }
      String kind = String.valueOf(map.keySet().iterator().next());
      Object entry = map.values().iterator().next();
      String at = place + "." + kind;
      return switch (kind) {
        case STATIC_INSTANCE -> staticInstance(entry, at);
        case CONTAINER_BINDING -> containerBinding(entry, at);
        case ALIAS -> alias(entry, at);
        default -> {
          problem(
              place,
              "A Backup has no Instance Source of kind "
                  + kind
                  + ". It has static-instance, container-binding and alias.");
          yield null;
        }
      };
    }

    private Publication publication(Object value, String place) {
      if (value == null) {
        return null;
      }
      if (NONE.equals(value)) {
        return new Unpublished();
      }
      if (!(value instanceof Map<?, ?>)) {
        problem(
            place,
            "A publication is none, or a mapping with scope, route-profiles and access-log.");
        return null;
      }
      Map<String, Object> entry =
          keys(value, place, Set.of("scope", "route-profiles", "access-log"));
      return new Published(
          text(entry.get("scope"), place + ".scope"),
          texts(entry.get("route-profiles"), place + ".route-profiles"),
          flag(entry.get("access-log"), place + ".access-log", false));
    }

    private StaticInstance staticInstance(Object value, String place) {
      Map<String, Object> entry =
          keys(value, place, Set.of("host", "port", "check", "tags", "meta"));
      Map<String, String> meta = null;
      if (entry.get("meta") != null) {
        meta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> pair :
            keys(entry.get("meta"), place + ".meta", null).entrySet()) {
          String text = text(pair.getValue(), place + ".meta." + pair.getKey());
          meta.put(pair.getKey(), text == null ? "" : text);
        }
      }
      return new StaticInstance(
          text(entry.get("host"), place + ".host"),
          port(entry.get("port"), place + ".port"),
          check(entry.get("check"), place + ".check"),
          texts(entry.get("tags"), place + ".tags"),
          meta);
    }

    private HealthCheck check(Object value, String place) {
      if (value == null) {
        return null;
      }
      Map<String, Object> check = keys(value, place, Set.of("type", "path", "interval", "timeout"));
      return new HealthCheck(
          text(check.get("type"), place + ".type"),
          text(check.get("path"), place + ".path"),
          text(check.get("interval"), place + ".interval"),
          text(check.get("timeout"), place + ".timeout"));
    }

    private ContainerBinding containerBinding(Object value, String place) {
      Map<String, Object> entry = keys(value, place, Set.of("container", "compose", "port"));
      int port = port(entry.get("port"), place + ".port");
      String container = text(entry.get("container"), place + ".container");
      if (entry.containsKey("compose") == (container != null)) {
        problem(place, "A Container Binding names either a container or a compose service.");
        return new ContainerBinding(container, null, null, port);
      }
      if (container != null) {
        return new ContainerBinding(container, null, null, port);
      }
      Map<String, Object> compose =
          keys(entry.get("compose"), place + ".compose", Set.of("project", "service"));
      return new ContainerBinding(
          null,
          required(compose.get("project"), place + ".compose.project"),
          required(compose.get("service"), place + ".compose.service"),
          port);
    }

    private Alias alias(Object value, String place) {
      Map<String, Object> entry = keys(value, place, Set.of("workstation", "service"));
      return new Alias(
          required(entry.get("workstation"), place + ".workstation"),
          required(entry.get("service"), place + ".service"));
    }

    /** {@code 0} when it is missing or not a whole number, with a problem. */
    private int port(Object value, String place) {
      if (value instanceof Integer port) {
        return port;
      }
      problem(
          place,
          value == null ? "The port is missing." : "The port " + value + " is not a number.");
      return 0;
    }

    private String required(Object value, String place) {
      String text = text(value, place);
      if (text == null) {
        problem(place, "The value is missing.");
      }
      return text;
    }

    /** A number or true reads as its text, as people write meta and tags without quotes. */
    private String text(Object value, String place) {
      if (value == null || value instanceof String) {
        return (String) value;
      }
      if (value instanceof Number || value instanceof Boolean) {
        return value.toString();
      }
      problem(place, "The value must be text.");
      return null;
    }

    /** {@code missing} when the file leaves it out. */
    private boolean flag(Object value, String place, boolean missing) {
      if (value == null) {
        return missing;
      }
      if (value instanceof Boolean flag) {
        return flag;
      }
      problem(place, "The value must be true or false.");
      return missing;
    }

    private List<String> texts(Object value, String place) {
      if (value == null) {
        return null;
      }
      return list(value, place, this::text);
    }

    private <T> List<T> list(Object value, String place, BiFunction<Object, String, T> read) {
      if (value == null) {
        return List.of();
      }
      if (!(value instanceof List<?> items)) {
        problem(place, "The value must be a list.");
        return List.of();
      }
      List<T> list = new ArrayList<>();
      for (int at = 0; at < items.size(); at++) {
        list.add(read.apply(items.get(at), place + "[" + at + "]"));
      }
      return list;
    }

    private <T> Map<String, T> entries(
        Object value, String place, BiFunction<Object, String, T> read) {
      Map<String, T> entries = new LinkedHashMap<>();
      keys(value, place, null)
          .forEach((name, entry) -> entries.put(name, read.apply(entry, place + "." + name)));
      return entries;
    }

    /**
     * The mapping's entries by their keys as text. {@code allowed} {@code null} takes any key; else
     * any other key is a problem.
     */
    private Map<String, Object> keys(Object value, String place, Set<String> allowed) {
      Map<String, Object> keys = new LinkedHashMap<>();
      if (value == null) {
        return keys;
      }
      if (!(value instanceof Map<?, ?> map)) {
        problem(place, "The value must be a mapping.");
        return keys;
      }
      map.forEach((key, entry) -> {
        String name = String.valueOf(key);
        String at = place.isEmpty() ? name : place + "." + name;
        if (allowed != null && !allowed.contains(name)) {
          problem(at, "A Backup has no key " + name + " here.");
        } else {
          keys.put(name, entry);
        }
      });
      return keys;
    }

    private void problem(String place, String reason) {
      problems.add(new BackupProblem(place, reason));
    }
  }
}
