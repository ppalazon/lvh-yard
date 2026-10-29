package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Static Instance to register.
 *
 * @param host the address it listens on; this Workstation's LAN address when left out
 * @param port the port it listens on
 */
record StaticInstanceRequest(String host, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port) {
}
