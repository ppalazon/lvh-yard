package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;

/**
 * With a path, the check is {@code http}, else {@code tcp}. Defaults: path {@code /}, interval 10
 * s, timeout 2 s. Script checks are not offered.
 */
public record HealthCheck(
    @Schema(allowableValues = {HealthCheck.HTTP, HealthCheck.TCP}) String type,
    @JsonInclude(JsonInclude.Include.NON_NULL) String path,
    String interval,
    String timeout) {

  static final String HTTP = "http";

  static final String TCP = "tcp";

  public boolean http() {
    return HTTP.equals(type);
  }

  /** Whole seconds as {@code 10s}, anything else as {@code 1500ms}. */
  static String format(Duration duration) {
    long millis = duration.toMillis();
    return millis % 1000 == 0 ? millis / 1000 + "s" : millis + "ms";
  }
}
