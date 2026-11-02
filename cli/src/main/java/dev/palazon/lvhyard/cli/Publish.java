package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code lvh-yard publish}: gives one of this Workstation's Services its hostname, or changes its
 * Scope, and prints any warning about it.
 */
@Command(
    name = "publish",
    mixinStandardHelpOptions = true,
    description =
        "Publishes a Service of this Workstation at <service>.<base-domain>, or changes its Scope.")
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
        "yard: every other Workstation also routes it, at <service>.<workstation>.<base-domain>.",
        "Default: local for a new Publication; one that exists keeps its Scope."
      })
  String scope;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    if (scope != null && !SCOPES.contains(scope)) {
      throw new ParameterException(
          spec.commandLine(),
          "Invalid value for option '--scope': " + scope + " is not local or yard.");
    }
    Dashboard.Service published = yard.dashboard().publish(service, scope);
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
            + (published.yardHostname() == null
                ? ""
                : "; other Workstations reach it at " + published.yardHostname())
            + ". It is "
            + published.state()
            + ".");
    out.flush();
    PrintWriter err = spec.commandLine().getErr();
    published.warnings().forEach(warning -> err.println("Warning: " + warning.message()));
    err.flush();
  }
}
