package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code lvh-yard status}: how LVH Yard on this Workstation is, part by part, with each part's
 * reason and what was found. It exits with 1 when any part is failing, or with {@code --strict}
 * when any part has a warning too, so a script can wait until LVH Yard is ready.
 */
@Command(
    name = "status",
    mixinStandardHelpOptions = true,
    description = {
      "Shows how LVH Yard works on this Workstation: Consul, Docker and the other parts, each ok,"
          + " warning or failing, with the reason.",
      "Exits with 1 when any part is failing, or has a warning with --strict."
    })
class Status implements Callable<Integer> {

  @ParentCommand Yard yard;

  @Option(names = "--strict", description = "Exit with 1 on a warning too.")
  boolean strict;

  @Spec CommandSpec spec;

  @Override
  public Integer call() {
    Dashboard.Status status = yard.dashboard().status();
    PrintWriter out = spec.commandLine().getOut();
    out.println("LVH Yard on this Workstation: " + status.overall());
    for (Dashboard.StatusPart part : status.parts()) {
      out.println("  " + mark(part.state()) + part.name() + ": " + part.reason());
      for (Dashboard.StatusDetail detail : part.details()) {
        out.println(
            "          "
                + detail.name()
                + ": "
                + detail.value()
                + (detail.state() == null || "ok".equals(detail.state())
                    ? ""
                    : " (" + detail.state() + ")"));
      }
    }
    out.flush();
    return switch (status.overall()) {
      case "ok" -> 0;
      case "warning" -> strict ? 1 : 0;
      default -> 1;
    };
  }

  /** The state as a column 6 wide: {@code ok}, {@code WARN} or {@code FAIL}. */
  private static String mark(String state) {
    return switch (state) {
      case "ok" -> "ok    ";
      case "warning" -> "WARN  ";
      default -> "FAIL  ";
    };
  }
}
