package dev.palazon.lvhyard.dashboard;

import org.springframework.test.context.TestPropertySource;

/**
 * A dashboard that follows the real Docker, for tests that need a container that Consul or Traefik
 * reaches. Spring gives it a context of its own, with its own test Yard.
 */
@TestPropertySource(properties = "test.docker=real")
abstract class RealDockerIT extends DashboardIT {}
