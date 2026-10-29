package dev.palazon.lvhyard.dashboard.consul;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this Workstation's Consul agent answers, from {@code YARD_CONSUL_HTTP_PORT}.
 *
 * @param url the agent's HTTP API, on loopback
 */
@ConfigurationProperties("lvh-yard.consul")
public record ConsulSettings(URI url) {
}
