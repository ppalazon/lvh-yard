package dev.palazon.lvhyard.cli;

import java.nio.file.Path;
import java.util.Map;

/**
 * The folder that holds a Workstation's {@code compose.yaml}, {@code .env} and {@code
 * route-profiles.yaml}: {@code $XDG_CONFIG_HOME/lvh-yard}, else {@code ~/.config/lvh-yard}.
 */
final class ConfigFolder {

  private ConfigFolder() {}

  /** {@code YARD_CONFIG_DIR} when set, else the {@linkplain #standard default folder}. */
  static Path forReading(Map<String, String> environment) {
    String dir = environment.get(Settings.CONFIG_DIR);
    return dir != null && !dir.isBlank() ? Path.of(dir) : standard(environment);
  }

  static Path standard(Map<String, String> environment) {
    String xdg = environment.get("XDG_CONFIG_HOME");
    if (xdg != null && !xdg.isBlank()) {
      return Path.of(xdg, "lvh-yard");
    }
    // HOME first: the native executable's user.home comes from the user database, not the shell.
    String home = environment.getOrDefault("HOME", System.getProperty("user.home"));
    return Path.of(home, ".config", "lvh-yard");
  }
}
