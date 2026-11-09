package dev.palazon.lvhyard.dashboard.services;

/**
 * Every field may be left out.
 *
 * @param port the container port, needed when the container does not run or does not have exactly
 *     one TCP port
 * @param service the Service name, chosen from the Binding target when left out. The Service must
 *     exist
 */
public record ContainerRegistrationRequest(Integer port, String service) {

  public static final ContainerRegistrationRequest DEFAULTS =
      new ContainerRegistrationRequest(null, null);
}
