package dev.palazon.lvhyard.dashboard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Workstation this dashboard runs on, from {@code YARD_WORKSTATION} and {@code
 * YARD_BASE_DOMAIN}.
 *
 * @param name the Workstation's name, unique in the Yard and a single DNS label
 * @param baseDomain this Workstation's Base Domain, which every hostname it routes ends with; each
 *     Workstation of the Yard picks its own
 */
@ConfigurationProperties("lvh-yard.workstation")
public record Workstation(String name, String baseDomain) {

  /** Stands for the Base Domain of whichever Workstation reads a hostname that holds it. */
  public static final String VIEWERS_BASE_DOMAIN = "<base-domain>";

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

  /**
   * Where every other Workstation reaches this Workstation's own {@code service}: {@code
   * <service>.<name>.<base-domain>}, with {@code <base-domain>} left as written, because each of
   * them ends it with its own Base Domain.
   */
  public String yardHostname(String service) {
    return service + "." + name + "." + VIEWERS_BASE_DOMAIN;
  }
}
