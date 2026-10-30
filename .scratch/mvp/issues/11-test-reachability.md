# 11: Test Reachability

**What to build:** A developer tests a Service's Reachability from the Workstation they are using, with a "Test" button on the Service detail page or `yard test`. The test connects straight to each Instance and through the local Traefik by the Service's hostname, and shows the results without storing them. The Reachability tests use a real Traefik.

**Blocked by:** 03 (Publish a Service on its own Workstation)

**Status:** done

- [x] The test connects to each Instance's address and port from this Workstation, and reports success or the error.
- [x] For a published Service, the test also requests each of its hostnames through the local Traefik, and reports the answer.
- [x] An unpublished Service is tested straight to its Instances only, and the result says it has no Route.
- [x] Results are shown on the Service detail page and in the API, and are never stored.
- [x] Remote Services can be tested too.
- [x] `yard test` prints the same results, with a non-zero exit code when any check fails.
