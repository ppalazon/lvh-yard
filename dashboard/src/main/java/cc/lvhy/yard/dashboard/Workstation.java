package cc.lvhy.yard.dashboard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Workstation this dashboard runs on, from {@code YARD_WORKSTATION} and {@code
 * YARD_BASE_DOMAIN}.
 */
@ConfigurationProperties("lvh-yard.workstation")
public record Workstation(String name, String baseDomain) {

  /** Stands for the Base Domain of whichever Workstation reads a hostname that holds it. */
  public static final String VIEWERS_BASE_DOMAIN = "<base-domain>";

  public boolean is(String workstation) {
    return name.equals(workstation);
  }

  /** {@code <name>.<base-domain>}. */
  public String hostname(String name) {
    return name + "." + baseDomain;
  }

  /** {@code <service>.<home>.<base-domain>} (ADR-0003). */
  public String hostname(String home, String service) {
    return service + "." + home + "." + baseDomain;
  }

  /**
   * {@code <service>.<name>.<base-domain>}, with {@code <base-domain>} left as written, because
   * each other Workstation ends it with its own Base Domain.
   */
  public String yardHostname(String service) {
    return service + "." + name + "." + VIEWERS_BASE_DOMAIN;
  }
}
