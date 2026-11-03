package dev.palazon.lvhyard.cli;

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

  /** The value of {@code key}, when it is set and not commented. */
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

  /** Every setting that is not commented, by key, in the file's order. */
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

  /** Whether the file has a line for {@code key}, commented or not. */
  boolean has(String key) {
    return lines.stream()
        .map(SETTING::matcher)
        .anyMatch(setting -> setting.matches() && setting.group(2).equals(key));
  }

  /** Adds {@code line} at the end. */
  EnvFile append(String line) {
    lines.add(line);
    return this;
  }

  String text() {
    return String.join("\n", lines) + "\n";
  }
}
