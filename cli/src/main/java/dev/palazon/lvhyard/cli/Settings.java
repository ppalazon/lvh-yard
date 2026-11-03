package dev.palazon.lvhyard.cli;

/**
 * The names the CLI reads or writes: the settings of a Workstation's {@code .env}, as {@code
 * .env.example} names them, and the environment variables that point the CLI at a Workstation.
 */
final class Settings {

  // Settings of the .env.
  static final String WORKSTATION = "YARD_WORKSTATION";
  static final String BASE_DOMAIN = "YARD_BASE_DOMAIN";
  static final String GOSSIP_KEY = "YARD_GOSSIP_KEY";
  static final String PEERS = "YARD_PEERS";
  static final String UID = "YARD_UID";
  static final String GID = "YARD_GID";
  static final String DOCKER_GID = "YARD_DOCKER_GID";
  static final String COMPOSE_PROFILES = "COMPOSE_PROFILES";
  static final String DASHBOARD_BIND_ADDRESS = "YARD_DASHBOARD_BIND_ADDRESS";
  static final String DASHBOARD_PORT = "YARD_DASHBOARD_PORT";
  static final String CONSUL_SERVER_PORT = "YARD_CONSUL_SERVER_PORT";
  static final String CONSUL_SERF_WAN_PORT = "YARD_CONSUL_SERF_WAN_PORT";

  // Environment variables.
  /** The folder of the {@code .env} to find the dashboard from. */
  static final String CONFIG_DIR = "YARD_CONFIG_DIR";

  /** The dashboard's address, over the {@code .env}'s. */
  static final String DASHBOARD_URL = "YARD_DASHBOARD_URL";

  private Settings() {}
}
