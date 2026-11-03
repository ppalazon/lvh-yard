package dev.palazon.lvhyard.cli;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code image:} lines of a {@code compose.yaml}'s services, read and changed by line, so every
 * other line stays byte for byte: the file is never reparsed nor reformatted.
 */
final class ComposeImages {

  private static final Pattern TOP_LEVEL = Pattern.compile("^([A-Za-z0-9_.-]+):.*");
  private static final Pattern SERVICE = Pattern.compile("^  ([A-Za-z0-9_.-]+):\\s*(#.*)?$");
  private static final Pattern IMAGE =
      Pattern.compile("^(\\s{3,}image:\\s*)([\"']?)([^\"'\\s]+)\\2(.*)$");

  private final String[] lines;

  /** The image line of each service, by service name, as its index in {@link #lines}. */
  private final Map<String, Integer> images = new LinkedHashMap<>();

  private ComposeImages(String text) {
    lines = text.split("\n", -1);
    String top = null;
    String service = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      Matcher topLevel = TOP_LEVEL.matcher(line);
      Matcher header = SERVICE.matcher(line);
      if (topLevel.matches()) {
        top = topLevel.group(1);
        service = null;
      } else if ("services".equals(top) && header.matches()) {
        service = header.group(1);
      } else if (service != null && IMAGE.matcher(line).matches()) {
        images.putIfAbsent(service, i);
      }
    }
  }

  static ComposeImages parse(String text) {
    return new ComposeImages(text);
  }

  /** {@code service}'s image, when it has an {@code image:} line. */
  Optional<String> image(String service) {
    return Optional.ofNullable(images.get(service)).map(i -> matcher(i).group(3));
  }

  /** Sets {@code service}'s image, keeping the rest of its line: indent, quotes and comment. */
  void setImage(String service, String image) {
    int i = images.get(service);
    Matcher line = matcher(i);
    lines[i] = line.group(1) + line.group(2) + image + line.group(2) + line.group(4);
  }

  /** The services among {@code services} without an {@code image:} line. */
  List<String> missing(List<String> services) {
    return services.stream().filter(service -> !images.containsKey(service)).toList();
  }

  String text() {
    return String.join("\n", Arrays.asList(lines));
  }

  private Matcher matcher(int i) {
    Matcher matcher = IMAGE.matcher(lines[i]);
    if (!matcher.matches()) {
      throw new IllegalStateException("Line " + (i + 1) + " is no longer an image line");
    }
    return matcher;
  }
}
