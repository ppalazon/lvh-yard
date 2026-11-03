package dev.palazon.lvhyard.dashboard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Workstation this dashboard runs on, from {@code YARD_WORKSTATION} and {@code
 * YARD_BASE_DOMAIN}.
 *
 * @param name the Workstation's name, unique in the Yard and a single DNS label
 * @param baseDomain the Yard's base domain, which every hostname ends with
 */
@ConfigurationProperties("lvh-yard.workstation")
public record Workstation(String name, String baseDomain) {

  /** Whether {@code workstation} names this Workstation, not another one of the Yard. */
  public boolean is(String workstation) {
    return name.equals(workstation);
  }

  /** {@code name} as one of this Workstation's own hostnames: {@code <name>.<base-domain>}. */
  public String hostname(String name) {
    return name + "." + baseDomain;
  }

  /**
   * Where this Workstation reaches {@code service} of another Workstation, {@code home}: {@code
   * <service>.<home>.<base-domain>} (ADR-0003).
   */
  public String hostname(String home, String service) {
    return service + "." + home + "." + baseDomain;
  }
}
