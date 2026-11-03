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
      "Shows whether the Core Services work on this Workstation.",
      "Exits with 1 when a part fails."
    })
class Status implements Callable<Integer> {

  @ParentCommand Yard yard;

  @Option(names = "--strict", description = "Also exit with 1 on a warning.")
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
    return State.of(status.overall()).fails(strict) ? 1 : 0;
  }

  /** The state as a column 6 wide: {@code ok}, {@code WARN} or {@code FAIL}. */
  private static String mark(String state) {
    return State.of(state).mark;
  }

  /**
   * A state as the dashboard names it, how it is printed, and whether it makes the command fail.
   */
  private enum State {
    OK("ok    "),
    WARNING("WARN  "),
    FAILING("FAIL  ");

    final String mark;

    State(String mark) {
      this.mark = mark;
    }

    /** The state the dashboard calls {@code name}; failing for one this command does not know. */
    static State of(String name) {
      return switch (name) {
        case "ok" -> OK;
        case "warning" -> WARNING;
        default -> FAILING;
      };
    }

    /** Whether the command exits with 1 for this state; a warning only with {@code --strict}. */
    boolean fails(boolean strict) {
      return this == FAILING || (this == WARNING && strict);
    }
  }
}
