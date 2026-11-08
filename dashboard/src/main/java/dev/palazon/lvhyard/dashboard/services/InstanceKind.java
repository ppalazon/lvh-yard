package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** What an Instance Priority orders. */
public enum InstanceKind {
  STATIC,
  BOUND,

  /** The Remote Service of an Alias with the same name as the Service. */
  ALIAS;

  /** For a Publication that has no order of its own, such as one from before Instance Priority. */
  public static final List<InstanceKind> DEFAULT_PRIORITY = List.of(STATIC, BOUND, ALIAS);

  /** A Foreign Instance counts as a Static Instance. */
  static InstanceKind of(Instance instance) {
    return instance.bound() ? BOUND : STATIC;
  }

  /**
   * The message of a refusal says what is wrong, for the person who gave {@code values}. No kind
   * means the Instance Priority is off.
   */
  public static List<InstanceKind> priority(List<String> values) {
    List<InstanceKind> kinds = new ArrayList<>();
    for (String value : values) {
      InstanceKind kind =
          Arrays.stream(values())
              .filter(candidate -> candidate.value().equals(value))
              .findFirst()
              .orElseThrow(() ->
                  invalidPriority(
                      "The Instance Priority has the unknown kind "
                          + value
                          + ". The kinds are static, bound and alias."));
      if (kinds.contains(kind)) {
        throw invalidPriority("The Instance Priority has the kind " + value + " more than once.");
      }
      kinds.add(kind);
    }
    return List.copyOf(kinds);
  }

  private static InvalidPriorityException invalidPriority(String message) {
    return new InvalidPriorityException(message);
  }

  @JsonValue
  public String value() {
    return name().toLowerCase();
  }
}
