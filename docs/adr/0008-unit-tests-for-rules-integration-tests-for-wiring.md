# Unit tests for rules, integration tests for wiring

The tests had one seam: the running dashboard, with a test Yard of real Consul, Docker containers and Traefik behind it. Each edge case paid for Consul, containers and waits, so the test time grew with each new case. Now the tests have two seams. Rules with no I/O get a unit test for each edge case. Examples are the Route choice, the input checks, the Backup format and the CLI output. A unit test starts no Spring context, no Docker container and no other process. Each feature keeps one or a few integration tests through the running dashboard. Those tests prove that the parts work together, not every case.

## Considered Options

- One seam, with faster test infrastructure only: rejected. The reset between tests and shorter check intervals took `mise run check` from 81 s to 59 s. Each new edge case still adds seconds.
- Running only the tests of the changed package: rejected. Most integration tests go through the REST API, so a change in one package can break tests in any package.
- Spring slice tests, such as `@WebMvcTest`, as a third kind: rejected. Two kinds keep the rule simple: a test either needs no I/O or goes through the whole dashboard.

## Consequences

- Unit test classes end in `Test` and run with Surefire. Integration test classes end in `IT` and run with Failsafe, in four JVMs that each have their own test Yard.
- `mise run test:unit` runs only the unit tests. `mise run check` runs both kinds before each commit.
- A unit test lives in the package of the class that it tests, so that it can call package-private methods.
- Refusals that need state in Consul, such as a container that a binding already follows, stay as integration tests.
