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

@Command(
    name = "publish",
    mixinStandardHelpOptions = true,
    description = "Publishes a Service, or changes its Scope.")
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
        "Default: local for a new Publication, else no change."
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
            + ".");
    out.flush();
    PrintWriter err = spec.commandLine().getErr();
    published.warnings().forEach(warning -> err.println("Warning: " + warning.message()));
    err.flush();
  }
}
