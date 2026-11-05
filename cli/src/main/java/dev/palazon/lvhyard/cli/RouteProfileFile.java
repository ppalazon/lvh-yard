package dev.palazon.lvhyard.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Route Profiles in Traefik's file format: a YAML document with {@code http.middlewares.<name>}.
 */
final class RouteProfileFile {

  /** Quotes a string such as "100", so the file reads back as the same middleware. */
  private static final YAMLMapper YAML =
      YAMLMapper.builder()
          .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
          .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
          .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
          .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
          .build();

  private RouteProfileFile() {}

  /** {@code profiles} maps each name to its middleware. */
  static String write(Map<String, Object> profiles) {
    return YAML.writeValueAsString(Map.of("http", Map.of("middlewares", profiles)));
  }

  /** Each name of {@code text} with its middleware, in the order of the file. */
  static Map<String, Object> read(String text) {
    JsonNode root;
    try {
      root = YAML.readTree(text);
    } catch (JacksonException e) {
      throw new YardException("The file is not valid YAML: " + e.getOriginalMessage());
    }
    JsonNode middlewares = root == null ? null : root.path("http").path("middlewares");
    if (middlewares == null
        || !middlewares.isObject()
        || root.size() != 1
        || root.get("http").size() != 1) {
      throw new YardException(
          "The file must hold only http.middlewares, with each profile's name and its"
              + " middleware.");
    }
    if (middlewares.isEmpty()) {
      throw new YardException("The file names no Route Profile.");
    }
    Map<String, Object> profiles = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> profile : middlewares.properties()) {
      profiles.put(profile.getKey(), YAML.treeToValue(profile.getValue(), Object.class));
    }
    return profiles;
  }
}
