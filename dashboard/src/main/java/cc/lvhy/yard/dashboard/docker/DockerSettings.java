package cc.lvhy.yard.dashboard.docker;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** From {@code DOCKER_HOST}, such as {@code unix:///var/run/docker.sock}. */
@ConfigurationProperties("lvh-yard.docker")
public record DockerSettings(URI host) {}
