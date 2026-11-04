package dev.palazon.lvhyard.dashboard.profiles;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** From {@code YARD_ROUTE_PROFILES}: where {@code route-profiles.yaml} is. */
@ConfigurationProperties("lvh-yard.route-profiles")
public record RouteProfilesSettings(Path path) {}
