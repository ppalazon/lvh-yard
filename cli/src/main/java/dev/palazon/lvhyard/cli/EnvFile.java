package dev.palazon.lvhyard.cli;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code .env} file as lines of text, so its comments and order stay as they are. A setting is a
 * line {@code KEY=value}; a commented one, {@code #KEY=value}, is a setting left at its default.
 */
final class EnvFile {

  /** A setting's line, commented or not: group 1 is the #, 2 the key, 3 the value. */
  private static final Pattern SETTING = Pattern.compile("(#)?([A-Z][A-Z0-9_]*)=(.*)");

  private final List<String> lines;

  private EnvFile(List<String> lines) {
    this.lines = lines;
  }

  static EnvFile parse(String text) {
    return new EnvFile(new ArrayList<>(text.lines().toList()));
  }

  Optional<String> get(String key) {
    for (String line : lines) {
      Matcher setting = SETTING.matcher(line);
      if (setting.matches() && setting.group(1) == null && setting.group(2).equals(key)) {
        return Optional.of(setting.group(3));
      }
    }
    return Optional.empty();
  }

  /**
   * The value of {@code key} when it is set and not blank, else the built-in {@code .env}'s: as
   * {@code compose.yaml} reads an empty setting, with {@code ${KEY:-default}}.
   */
  String valueOrDefault(String key) {
    return get(key)
        .filter(value -> !value.isBlank())
        .or(() -> Templates.env().get(key))
        .orElseThrow(() -> new IllegalStateException(".env.example has no default for " + key));
  }

  /**
   * Reaches the wildcard {@code 0.0.0.0} through loopback, as the dashboard's {@code BindAddress}
   * does.
   */
  URI dashboardUrl() {
    String bind = valueOrDefault(Settings.DASHBOARD_BIND_ADDRESS);
    String host = bind.equals("0.0.0.0") ? "127.0.0.1" : bind;
    return URI.create("http://" + host + ":" + valueOrDefault(Settings.DASHBOARD_PORT));
  }

  /**
   * Sets {@code key} to {@code value} on its own line, uncommenting it, so its comment above stays.
   *
   * @throws IllegalStateException when the file has no line for {@code key}
   */
  EnvFile set(String key, String value) {
    for (int i = 0; i < lines.size(); i++) {
      Matcher setting = SETTING.matcher(lines.get(i));
      if (setting.matches() && setting.group(2).equals(key)) {
        lines.set(i, key + "=" + value);
        return this;
      }
    }
    throw new IllegalStateException(".env has no setting " + key);
  }

  /** The settings that are not commented, in the file's order. */
  Map<String, String> settings() {
    Map<String, String> settings = new LinkedHashMap<>();
    for (String line : lines) {
      Matcher setting = SETTING.matcher(line);
      if (setting.matches() && setting.group(1) == null) {
        settings.putIfAbsent(setting.group(2), setting.group(3));
      }
    }
    return settings;
  }

  boolean has(String key) {
    return lines.stream()
        .map(SETTING::matcher)
        .anyMatch(setting -> setting.matches() && setting.group(2).equals(key));
  }

  EnvFile append(String line) {
    lines.add(line);
    return this;
  }

  String text() {
    return String.join("\n", lines) + "\n";
  }
}
