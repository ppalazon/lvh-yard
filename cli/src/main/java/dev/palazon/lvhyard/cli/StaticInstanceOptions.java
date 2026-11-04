package dev.palazon.lvhyard.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Option;

/** The options of a Static Instance, besides its port. */
class StaticInstanceOptions {

  @Option(
      names = "--host",
      paramLabel = "<host>",
      description =
          "The address the Instance listens on (default: this Workstation's LAN address).")
  String host;

  @Option(
      names = "--check",
      paramLabel = "<type>",
      description =
          "The Health check: tcp connects to the port, http GETs --path (default: http with --path,"
              + " else tcp).")
  String check;

  @Option(
      names = "--path",
      paramLabel = "<path>",
      description = "The path an HTTP check GETs (default: /).")
  String path;

  @Option(
      names = "--interval",
      paramLabel = "<duration>",
      description = "Time between checks, 1s or more, such as 5s or 1m (default: 10s).")
  String interval;

  @Option(
      names = "--timeout",
      paramLabel = "<duration>",
      description = "How long the check waits for an answer, such as 500ms (default: 2s).")
  String timeout;

  @Option(
      names = "--tag",
      paramLabel = "<tag>",
      description = "A word that labels the Instance. Repeat for more.")
  List<String> tags = new ArrayList<>();

  @Option(
      names = "--meta",
      paramLabel = "<key=value>",
      description = "A note about the Instance. Repeat for more.")
  Map<String, String> meta = new LinkedHashMap<>();

  Dashboard.Check healthCheck() {
    return new Dashboard.Check(check, path, interval, timeout);
  }

  /** The names of the options that were given, in the order of the help. */
  List<String> given() {
    List<String> given = new ArrayList<>();
    if (host != null) {
      given.add("--host");
    }
    for (String[] option :
        new String[][] {
          {"--check", check}, {"--path", path}, {"--interval", interval}, {"--timeout", timeout}
        }) {
      if (option[1] != null) {
        given.add(option[0]);
      }
    }
    if (!tags.isEmpty()) {
      given.add("--tag");
    }
    if (!meta.isEmpty()) {
      given.add("--meta");
    }
    return given;
  }
}
