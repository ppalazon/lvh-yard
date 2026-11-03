package dev.palazon.lvhyard.dashboard.status;

/** Where each {@link StatusPart} comes in the status, as the spec lists them. */
final class StatusOrder {

  static final int CONSUL = 10;

  static final int TRAEFIK = 20;

  static final int DOCKER = 30;

  static final int CORE_SERVICES = 40;

  private StatusOrder() {}
}
