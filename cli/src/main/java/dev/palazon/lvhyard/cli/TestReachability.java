package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** An Out-of-range Instance is skipped, and that is not a failure. */
@Command(
    name = "test",
    mixinStandardHelpOptions = true,
    description = {
      "Tests whether this Workstation reaches a Service.",
      "Exits with 1 when a test fails."
    })
class TestReachability implements Callable<Integer> {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<service>", description = "The Service's name.")
  String service;

  @Option(
      names = "--workstation",
      paramLabel = "<name>",
      description = "The Service's Home Workstation (default: this one).")
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
        out.println("  skip  Instance " + instance.address() + ": out of range");
        skipped++;
        continue;
      }
      String routed =
          tested.routing() == null
              ? ""
              : instance.isRouted() ? " (in the Route)" : " (not in the Route now)";
      out.println(
          line(instance.reached(), "Instance " + instance.address() + routed, instance.error()));
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
    if (tested.routing() != null) {
      out.println(routingNow(tested));
    }
    int checks = tested.instances().size() - skipped + tested.routes().size();
    out.println(
        (failed == 0
                ? "Every check passed"
                : failed + " of " + checks + " check" + (checks == 1 ? "" : "s") + " failed")
            + (skipped == 0 ? "." : ". " + skipped + " skipped."));
    out.flush();
    return tested.passed() ? 0 : 1;
  }

  private static String routingNow(Dashboard.Reachability tested) {
    String kind = tested.routing().kind();
    // Health is not in the answer, so off cannot tell passing Instances from none passing.
    if (tested.routing().isOff()) {
      return "The Instance Priority is off, so the Route uses every passing Instance,"
          + " or every Instance in range if none passes.";
    }
    if (kind == null) {
      return "No Instance passes, so the Route uses every Instance in range.";
    }
    if (kind.equals("alias")) {
      Dashboard.Alias alias = tested.alias();
      return "The Route sends requests to the Alias's Remote Service"
          + (alias == null ? "." : ": " + alias.service() + " on " + alias.workstation() + ".");
    }
    return "The Route uses the " + kind + " Instance Source " + tested.routing().source() + " now.";
  }

  private static String line(boolean reached, String what, String detail) {
    return "  " + (reached ? "ok    " : "FAIL  ") + what + (detail == null ? "" : ": " + detail);
  }
}
