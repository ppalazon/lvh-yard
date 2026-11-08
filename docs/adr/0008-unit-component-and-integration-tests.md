# Unit, component and integration tests

The tests had one seam: the running dashboard, with a test Yard of real Consul, Docker containers and Traefik behind it. Each edge case paid for Consul, containers and waits, so the test time grew with each new case. Now the tests have three kinds:

- A unit test starts no Spring context and no real system. It tests one class or rule, for each edge case. Examples are the Route choice, the input checks, the Backup format and the CLI output.
- A component test starts the dashboard with Spring and goes through HTTP. Docker and Consul are in-memory fakes of the dashboard's own `Docker` and `Consul` interfaces.
- An integration test uses at least one real system: Docker, Consul, Traefik, or another process such as `lvh-yard` or `docker compose`. Each feature keeps one or a few of these tests. They prove that the parts work together, not every case.

## Considered Options

- One seam, with faster test infrastructure only: rejected. The reset between tests and shorter check intervals took `mise run check` from 81 s to 59 s. Each new edge case still adds seconds.
- Running only the tests of the changed package: rejected. Most tests go through the REST API, so a change in one package can break tests in any package.
- WireMock in place of Consul and Docker: rejected. Most tests write and then read back, so the stubs must copy Consul's state in JSON. Blocking queries and the Docker event stream are also hard to fake at the HTTP level. A fake behind the dashboard's own interface keeps state in plain maps.

## Consequences

- Unit test classes end in `Test` and component test classes end in `ComponentTest`. Surefire runs both. Integration test classes end in `IT`, and Failsafe runs them.
- `mise run test:unit` and `mise run test:component` run one kind each. `mise run check` runs all three kinds before each commit.
- A contract test runs the same cases against each fake and its real system. So a fake cannot drift from what the real system does. A fake copies only the behavior that its contract test pins.
- A unit test lives in the package of the class that it tests, so that it can call package-private methods.
- Tests that need what only a real system shows stay integration tests. Examples are a passing health check, WAN federation, Traefik routing, and the other Workstation's own dashboard.
