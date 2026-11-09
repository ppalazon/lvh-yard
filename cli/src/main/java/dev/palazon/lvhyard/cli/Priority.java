package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "priority",
    mixinStandardHelpOptions = true,
    description = {
      "Prints the Instance Sources of a Service in order, or sets their order.",
      "With Instance Priority on, the Route uses the first source with a passing Instance."
    })
class Priority implements Runnable {

  @ParentCommand Yard yard;

  @Parameters(index = "0", paramLabel = "<service>", description = "The Service's name.")
  String service;

  @Parameters(
      index = "1..*",
      arity = "0..*",
      paramLabel = "<id>",
      description =
          "Instance Source IDs, as this command prints them. They go first, in this order, and the"
              + " other sources follow in their order.")
  List<String> order;

  @ArgGroup(exclusive = true, multiplicity = "0..1")
  Toggle toggle;

  static class Toggle {

    @Option(
        names = "--on",
        required = true,
        description = "The Route uses the first source with a passing Instance.")
    boolean on;

    @Option(
        names = "--off",
        required = true,
        description = "Every passing Instance of every source shares the Route. The order stays.")
    boolean off;
  }

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard dashboard = yard.dashboard();
    if ((order != null && !order.isEmpty()) || toggle != null) {
      dashboard.setInstancePriority(
          service,
          order == null || order.isEmpty() ? null : order,
          toggle == null ? null : toggle.on);
    }
    print(dashboard.ownService(service));
  }

  private void print(Dashboard.Service found) {
    PrintWriter out = spec.commandLine().getOut();
    out.println(
        "Instance Priority of "
            + found.name()
            + ": "
            + (found.instancePriority()
                ? "on. The Route uses the first source with a passing Instance."
                : "off. Every passing Instance of every source shares the Route."));
    if (found.sources().isEmpty()) {
      out.println("  No Instance Sources yet.");
    }
    String used = found.routing() == null ? null : found.routing().source();
    for (int i = 0; i < found.sources().size(); i++) {
      Dashboard.Source source = found.sources().get(i);
      out.println(
          "  "
              + (i + 1)
              + ". "
              + source.id()
              + "  "
              + describe(found, source)
              + (source.id().equals(used) ? "  (in use)" : ""));
    }
    out.flush();
  }

  private static String describe(Dashboard.Service found, Dashboard.Source source) {
    return switch (source.kind()) {
      case "bound" ->
          found.containerBindings().stream()
              .filter(binding -> binding.id().equals(source.id()))
              .findFirst()
              .map(binding ->
                  "Container Binding to " + binding.follows() + ", port " + binding.port())
              .orElse("Container Binding");
      case "alias" -> {
        String[] target = source.id().split("/", 2);
        yield "Alias to " + (target.length < 2 ? "" : target[1]) + " on " + target[0];
      }
      case "static" -> "Static Instance " + address(found, source);
      case "foreign" -> "Foreign Instance " + address(found, source);
      // A newer dashboard can send a kind that this lvh-yard does not know.
      default -> source.kind();
    };
  }

  private static String address(Dashboard.Service found, Dashboard.Source source) {
    return found.instances().stream()
        .filter(instance -> source.instances().contains(instance.id()))
        .map(instance -> instance.host() + ":" + instance.port())
        .findFirst()
        .orElse("");
  }
}
