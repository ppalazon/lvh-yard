package cc.lvhy.yard.dashboard.services;

/**
 * The Remote Service of an Alias. Its {@link #id} is the Alias's Instance Source ID.
 *
 * @param workstation the Home Workstation of the Remote Service
 * @param service the name of the Remote Service
 */
public record AliasTarget(String workstation, String service) {

  /**
   * An {@code id} without a slash gives an empty {@link #service}, which the name check refuses.
   */
  public static AliasTarget parse(String id) {
    String[] parts = id.split("/", 2);
    return new AliasTarget(parts[0], parts.length < 2 ? "" : parts[1]);
  }

  /** {@code <workstation>/<service>}. */
  public String id() {
    return workstation + "/" + service;
  }

  /** Such as {@code grafana on ws-b}. */
  public String label() {
    return service + " on " + workstation;
  }
}
