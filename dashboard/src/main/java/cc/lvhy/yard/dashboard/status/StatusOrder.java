package cc.lvhy.yard.dashboard.status;

/** Where each {@link PartReader} comes in the status, as the spec lists them. */
final class StatusOrder {

  static final int CONSUL = 10;

  static final int TRAEFIK = 20;

  static final int DOCKER = 30;

  private StatusOrder() {}
}
