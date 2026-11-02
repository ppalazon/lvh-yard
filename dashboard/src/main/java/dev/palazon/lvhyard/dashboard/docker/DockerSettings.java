package dev.palazon.lvhyard.dashboard.docker;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this Workstation's Docker answers, from {@code DOCKER_HOST}.
 *
 * @param host the Docker Engine API, such as {@code unix:///var/run/docker.sock}
 */
@ConfigurationProperties("lvh-yard.docker")
public record DockerSettings(URI host) {}
