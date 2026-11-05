package dev.palazon.lvhyard.dashboard.web;

import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.representer.Representer;

/** The text of one Route Profile in the page's text box: its middleware in Traefik's format. */
final class RouteProfileYaml {

  private RouteProfileYaml() {}

  /**
   * @return the middleware, such as {@code {"headers": {...}}}
   * @throws IllegalArgumentException with a message for the user, when the text is not one mapping
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> read(String text) {
    Object root;
    try {
      root = yaml().load(text);
    } catch (YAMLException e) {
      throw new IllegalArgumentException("The text is not valid YAML: " + e.getMessage(), e);
    }
    if (root == null) {
      throw new IllegalArgumentException("Write the Route options in the text box.");
    }
    if (!(root instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(
          "The text must map a Traefik middleware type to its options, such as headers:.");
    }
    return (Map<String, Object>) map;
  }

  static String write(Map<String, Object> middleware) {
    return yaml().dump(middleware);
  }

  /** Block style, as people write Traefik's files by hand. */
  private static Yaml yaml() {
    DumperOptions dump = new DumperOptions();
    dump.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dump.setIndicatorIndent(2);
    dump.setIndentWithIndicator(true);
    return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(dump), dump);
  }
}
