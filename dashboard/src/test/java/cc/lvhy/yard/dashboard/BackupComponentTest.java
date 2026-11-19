package cc.lvhy.yard.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;

/** The paths and helpers that the export and import component tests share. */
abstract class BackupComponentTest extends DashboardComponentTest {

  static final String BACKUP_PATH = "/api/workstation/backup";

  static final String IMPORT_PATH = BACKUP_PATH + "?mode=add";

  ResponseEntity<JsonNode> importBackup(String backup) {
    return postYaml(IMPORT_PATH, backup);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> yaml(String text) {
    return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  /** The entries of the sources of {@code service}, each without its kind. */
  @SuppressWarnings("unchecked")
  static List<Object> sources(Map<String, Object> backup, String service, String kind) {
    List<Map<String, Object>> sources =
        (List<Map<String, Object>>) map(map(backup.get("services")).get(service)).get("sources");
    return sources == null
        ? List.of()
        : sources.stream()
            .filter(source -> source.containsKey(kind))
            .map(source -> source.get(kind))
            .toList();
  }

  /** The Backup with only the entries named {@code names}, as other tests share this Consul. */
  static String only(String backup, Set<String> names) {
    Map<String, Object> file = new LinkedHashMap<>(yaml(backup));
    for (String key : List.of("route-profiles", "services")) {
      Map<String, Object> kept = new LinkedHashMap<>(map(file.get(key)));
      kept.keySet().retainAll(names);
      file.put(key, kept);
    }
    return new Yaml().dump(file);
  }

  /** The place of each problem of a refusal. */
  static List<String> problems(ResponseEntity<JsonNode> response) {
    return response
        .getBody()
        .get("problems")
        .valueStream()
        .map(problem -> problem.get("place").asString())
        .toList();
  }

  /** Each plan entry as one line, such as {@code skip service web: It exists already.}. */
  static List<String> entries(ResponseEntity<JsonNode> response) {
    List<String> lines = new ArrayList<>();
    for (JsonNode entry : response.getBody().get("entries")) {
      StringBuilder line =
          new StringBuilder(entry.get("action").asString())
              .append(' ')
              .append(entry.get("kind").asString())
              .append(' ')
              .append(entry.get("name").asString());
      if (entry.hasNonNull("service")) {
        line.append(" of ").append(entry.get("service").asString());
      }
      if (entry.hasNonNull("reason")) {
        line.append(": ").append(entry.get("reason").asString());
      }
      lines.add(line.toString());
    }
    return lines;
  }
}
