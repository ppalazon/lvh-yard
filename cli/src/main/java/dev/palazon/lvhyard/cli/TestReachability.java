package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code lvh-yard test}: tests whether this Workstation reaches a Service, straight to each
 * Instance and through Traefik at each hostname, and prints each check. An Out-of-range Instance is
 * skipped, which is no failure. It exits with 1 when any check fails.
 */
@Command(
    name = "test",
    mixinStandardHelpOptions = true,
    description = {
      "Tests whether this Workstation reaches a Service: straight to each Instance, and through"
          + " Traefik at each of its hostnames.",
      "Exits with 1 when any check fails."
    })
class TestReachability implements Callable<Integer> {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<service>", description = "The Service's name.")
  String service;

  @Option(
      names = "--workstation",
      paramLabel = "<name>",
      description = "The Service's Home Workstation (default: this Workstation).")
  String workstation;

  @Spec CommandSpec spec;

  @Override
  public Integer call() {
    Dashboard.Reachability tested =
        workstation == null
            ? yard.dashboard().testOwn(service)
            : yard.dashboard().test(workstation, service);
    PrintWriter out = spec.commandLine().getOut();
    out.println(
        "Reachability of "
            + tested.service()
            + " on "
            + tested.workstation()
            + ", from this Workstation:");
    int failed = 0;
    int skipped = 0;
    for (Dashboard.InstanceReachability instance : tested.instances()) {
      if (instance.skipped()) {
        out.println(
            "  skip  Instance "
                + instance.address()
                + ": out of range, only "
                + tested.workstation()
                + " can connect to it");
        skipped++;
        continue;
      }
      out.println(line(instance.reached(), "Instance " + instance.address(), instance.error()));
      failed += instance.reached() ? 0 : 1;
    }
    for (Dashboard.RouteReachability route : tested.routes()) {
      out.println(
          line(
              route.reached(),
              "Route " + route.hostname(),
              route.reached() ? "answered " + route.status() : route.error()));
      failed += route.reached() ? 0 : 1;
    }
    if (tested.noRoute() != null) {
      out.println(tested.noRoute());
    }
    int checks = tested.instances().size() - skipped + tested.routes().size();
    out.println(
        (failed == 0
                ? "Every check passed"
                : failed + " of " + checks + " check" + (checks == 1 ? "" : "s") + " failed")
            + (skipped == 0 ? "." : "; " + skipped + " skipped."));
    out.flush();
    return tested.passed() ? 0 : 1;
  }

  /** One check, as {@code ok <what>: <detail>}, or with FAIL when it did not reach the Service. */
  private static String line(boolean reached, String what, String detail) {
    return "  " + (reached ? "ok    " : "FAIL  ") + what + (detail == null ? "" : ": " + detail);
  }
}
