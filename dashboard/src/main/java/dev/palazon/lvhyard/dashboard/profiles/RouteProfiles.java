package dev.palazon.lvhyard.dashboard.profiles;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.services.OutOfRange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * This Workstation's Route Profiles, from {@code route-profiles.yaml}. Each valid profile is copied
 * to this Workstation's Consul KV at {@link #KEY_PREFIX}{@code <name>}, where every Workstation
 * that routes a Publication naming it reads it (ADR-0004). The value is a JSON object in Traefik's
 * {@code http.middlewares} shape: the profile and every chain member it reaches, by their names in
 * the file.
 */
@Component
public class RouteProfiles {

  public static final String KEY_PREFIX = "lvh-yard/route-profiles/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Path path;

  private final OutOfRange outOfRange;

  private final Consul consul;

  private volatile RouteProfilesFile file = new RouteProfilesFile(null, List.of());

  /** The file's text the Consul KV holds the profiles of; {@code ""} for no file. */
  private String synced;

  RouteProfiles(RouteProfilesSettings settings, OutOfRange outOfRange, Consul consul) {
    this.path = settings.path();
    this.outOfRange = outOfRange;
    this.consul = consul;
  }

  /** What the file had when it was read last. */
  public RouteProfilesFile file() {
    return file;
  }

  /** The valid profiles, which Publications can name. */
  public List<String> names() {
    return file.profiles().stream().filter(RouteProfile::valid).map(RouteProfile::name).toList();
  }

  /**
   * Reads the file and, when it changed since the Consul KV took it, copies its valid profiles
   * there and deletes the others. A file that cannot be read writes nothing, so the KV keeps the
   * profiles read before.
   *
   * @throws RuntimeException when Consul does not answer; the next call tries again
   */
  synchronized void refresh() {
    String text;
    try {
      text = Files.readString(path);
    } catch (NoSuchFileException e) {
      text = "";
    } catch (IOException e) {
      file = new RouteProfilesFile(path + " cannot be read: " + e.getMessage(), file.profiles());
      return;
    }
    if (text.equals(synced)) {
      return;
    }
    RouteProfileReader.Reading reading = RouteProfileReader.read(text, outOfRange);
    if (reading.error() != null) {
      file = new RouteProfilesFile(reading.error(), file.profiles());
      synced = text;
      return;
    }
    file = new RouteProfilesFile(null, reading.profiles());
    copyToConsul(reading.definitions());
    synced = text;
  }

  private void copyToConsul(Map<String, Map<String, Object>> definitions) {
    Map<String, String> stored = consul.values(null, KEY_PREFIX);
    definitions.forEach((name, definition) -> {
      String value = JSON.writeValueAsString(definition);
      if (!Objects.equals(stored.get(KEY_PREFIX + name), value)) {
        consul.putValue(KEY_PREFIX + name, value);
      }
    });
    stored.keySet().stream()
        .filter(key -> !definitions.containsKey(key.substring(KEY_PREFIX.length())))
        .forEach(consul::deleteValue);
  }
}
