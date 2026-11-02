package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

/**
 * How an Instance's Health is checked: an HTTP GET that must answer 2xx, or a TCP connection to its
 * port. When an Instance is registered, every part may be left out: with a path the check is {@code
 * http}, else {@code tcp}.
 *
 * @param type {@code http} or {@code tcp}; script checks are not offered
 * @param path for an HTTP check, the path it GETs, {@code /} unless given; left out of a TCP check
 * @param interval how often the check runs, at least once a second, such as {@code 10s} or {@code
 *     1m}; 10 s unless given
 * @param timeout how long the check waits for an answer, such as {@code 500ms}; 2 s unless given
 */
public record HealthCheck(
    @Schema(allowableValues = {HealthCheck.HTTP, HealthCheck.TCP}) String type,
    @JsonInclude(JsonInclude.Include.NON_NULL) String path,
    String interval,
    String timeout) {

  static final String HTTP = "http";

  static final String TCP = "tcp";

  /** Whether it is an HTTP check. */
  public boolean http() {
    return HTTP.equals(type);
  }

  /**
   * {@code duration} as the API writes it: whole seconds as {@code 10s}, anything else as {@code
   * 1500ms}.
   */
  static String format(Duration duration) {
    long millis = duration.toMillis();
    return millis % 1000 == 0 ? millis / 1000 + "s" : millis + "ms";
  }
}
