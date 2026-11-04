package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "publish",
    mixinStandardHelpOptions = true,
    description = "Publishes a Service, or changes its Scope, Instance Priority or Route Profiles.")
class Publish implements Runnable {

  private static final List<String> SCOPES = List.of("local", "yard");

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<service>", description = "The Service's name.")
  String service;

  @Option(
      names = "--scope",
      paramLabel = "<scope>",
      description = {
        "local: only this Workstation routes it.",
        "yard: the other Workstations also route it, at <service>.<workstation>.<base-domain>.",
        "Default: local for a new Publication. An existing one keeps its Scope."
      })
  String scope;

  @Option(
      names = "--priority",
      paramLabel = "<kinds>",
      split = ",",
      description = {
        "The Instance kinds the Route prefers, in order: static, bound, alias. Or off.",
        "off: the passing Static and Bound Instances share the Route.",
        "Default: static,bound,alias for a new Publication. An existing one keeps its order."
      })
  List<String> priority;

  @ArgGroup(exclusive = true, multiplicity = "0..1")
  ProfileChoice profiles;

  static class ProfileChoice {

    @Option(
        names = "--profile",
        paramLabel = "<name>",
        required = true,
        description = {
          "A Route Profile from route-profiles.yaml. Repeat it for more, in the order the Route"
              + " applies them.",
          "Default: an existing Publication keeps its profiles."
        })
    List<String> names;

    @Option(names = "--no-profiles", required = true, description = "Removes all Route Profiles.")
    boolean none;
  }

  @Spec CommandSpec spec;

  @Override
  public void run() {
    if (scope != null && !SCOPES.contains(scope)) {
      throw new ParameterException(
          spec.commandLine(),
          "Invalid value for option '--scope': " + scope + " is not local or yard.");
    }
    List<String> kinds = List.of("off").equals(priority) ? List.of() : priority;
    List<String> chosenProfiles =
        profiles == null ? null : profiles.none ? List.of() : profiles.names;
    Dashboard.Service published = yard.dashboard().publish(service, scope, kinds, chosenProfiles);
    String chosen = published.publication().scope();
    PrintWriter out = spec.commandLine().getOut();
    out.println(
        "Published "
            + service
            + " at "
            + published.hostname()
            + ", with "
            + Character.toUpperCase(chosen.charAt(0))
            + chosen.substring(1)
            + " Scope"
            + (chosen.equals("yard")
                // Each other Workstation ends it with its own Base Domain, which this one cannot
                // know.
                ? ". Other Workstations reach it at "
                    + service
                    + "."
                    + published.workstation()
                    + ".<base-domain>"
                : "")
            + ". It is "
            + published.state()
            + "."
            + (published.profiles() == null || published.profiles().isEmpty()
                ? ""
                : " Route Profiles: " + String.join(", ", published.profiles()) + "."));
    out.flush();
    PrintWriter err = spec.commandLine().getErr();
    published.warnings().forEach(warning -> err.println("Warning: " + warning.message()));
    err.flush();
  }
}
