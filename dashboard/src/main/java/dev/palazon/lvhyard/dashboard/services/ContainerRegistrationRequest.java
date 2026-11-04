package dev.palazon.lvhyard.dashboard.services;

/**
 * Every field may be left out.
 *
 * @param port the container port, needed when the container does not run or does not have exactly
 *     one TCP port
 * @param service the Service name, by default the Compose service or the container name
 * @param publish {@code false} binds without publishing a new Service
 */
public record ContainerRegistrationRequest(Integer port, String service, Boolean publish) {

  public static final ContainerRegistrationRequest DEFAULTS =
      new ContainerRegistrationRequest(null, null, null);
}
