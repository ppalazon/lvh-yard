package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.consul.Consul.AgentService;
import cc.lvhy.yard.dashboard.consul.Consul.HealthEntry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what Consul says about a registration into an Instance: its Health, its check, its meta and
 * where it comes from. It reads nothing itself.
 */
final class InstanceMapping {

  /**
   * One number and unit of a Go duration, such as the {@code 1m} and the {@code 30s} of {@code
   * 1m30s}.
   */
  private static final Pattern GO_DURATION_PART =
      Pattern.compile("(\\d+(?:\\.\\d+)?)(h|ms|m|s|us|µs|ns)");

  private InstanceMapping() {}

  /**
   * The Instance that {@code entry} registers. One of a Remote Service is out of range here when
   * its address is in {@link OutOfRange}.
   */
  static Instance of(HealthEntry entry, boolean remote, OutOfRange outOfRange) {
    AgentService registration = entry.service();
    List<CheckResult> checks =
        entry.checks().stream()
            .map(check ->
                new CheckResult(
                    check.name(),
                    Health.ofConsulStatus(check.status()),
                    check.output() == null ? "" : check.output()))
            .toList();
    HealthCheck check =
        entry.checks().stream()
            .filter(consulCheck -> registration.id().equals(consulCheck.serviceId()))
            .findFirst()
            .map(consulCheck ->
                new HealthCheck(
                    consulCheck.type(),
                    registration.meta().get(Registrations.HTTP_PATH),
                    HealthCheck.format(goDuration(consulCheck.interval())),
                    HealthCheck.format(goDuration(consulCheck.timeout()))))
            .orElse(null);
    Map<String, String> meta = new TreeMap<>(registration.meta());
    meta.keySet().removeIf(key -> key.startsWith(InstanceRules.RESERVED_META));
    String binding = registration.meta().get(Registrations.CONTAINER_BINDING);
    boolean unpublished = registration.meta().containsKey(Registrations.UNPUBLISHED_CONTAINER_PORT);
    return new Instance(
        registration.id(),
        registration.address(),
        registration.port(),
        Health.worst(checks.stream().map(CheckResult::health).toList()),
        check,
        registration.tags(),
        meta,
        checks,
        remote && outOfRange.contains(registration.address()),
        binding,
        binding == null ? null : registration.meta().get(Registrations.CONTAINER),
        binding == null
            ? null
            : Integer.valueOf(registration.meta().get(Registrations.CONTAINER_PORT)),
        binding == null || unpublished ? null : registration.port(),
        unpublished,
        binding == null && !Registrations.isStaticInstance(registration.meta()));
  }

  /** A duration as Consul writes it, in Go's way, such as {@code 1m30s} or {@code 1.5s}. */
  static Duration goDuration(String text) {
    BigDecimal nanos = BigDecimal.ZERO;
    Matcher part = GO_DURATION_PART.matcher(text == null ? "" : text);
    while (part.find()) {
      BigDecimal unit =
          switch (part.group(2)) {
            case "h" -> BigDecimal.valueOf(3_600_000_000_000L);
            case "m" -> BigDecimal.valueOf(60_000_000_000L);
            case "s" -> BigDecimal.valueOf(1_000_000_000L);
            case "ms" -> BigDecimal.valueOf(1_000_000L);
            case "us", "µs" -> BigDecimal.valueOf(1_000L);
            default -> BigDecimal.ONE;
          };
      nanos = nanos.add(new BigDecimal(part.group(1)).multiply(unit));
    }
    return Duration.ofNanos(nanos.longValue());
  }
}
