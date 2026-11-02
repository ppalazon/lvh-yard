package dev.palazon.lvhyard.dashboard.reachability;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this Workstation's Traefik answers, from {@code YARD_HTTP_PORT}.
 *
 * @param url its entry point for plain HTTP, on loopback
 */
@ConfigurationProperties("lvh-yard.traefik")
public record TraefikSettings(URI url) {}
