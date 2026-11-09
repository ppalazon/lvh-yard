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
    description = "Publishes a Service, or changes its Scope, Route Profiles or access log.")
class Publish implements Runnable {

  private static final List<String> SCOPES = List.of("local", "yard");

  private static final List<String> SWITCHES = List.of("on", "off");

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

  @ArgGroup(exclusive = true, multiplicity = "0..1")
  ProfileChoice profiles;

  @Option(
      names = "--access-log",
      paramLabel = "<on|off>",
      description = {
        "on: Traefik logs each request to the Route. off: it does not.",
        "Default: off for a new Publication. An existing one keeps its own."
      })
  String accessLog;

  static class ProfileChoice {

    @Option(
        names = "--profile",
        paramLabel = "<name>",
        required = true,
        description = {
          "A Route Profile, as lvh-yard profiles lists them. Repeat it for more, in the order the"
              + " Route applies them.",
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
    if (accessLog != null && !SWITCHES.contains(accessLog)) {
      throw new ParameterException(
          spec.commandLine(),
          "Invalid value for option '--access-log': " + accessLog + " is not on or off.");
    }
    List<String> chosenProfiles =
        profiles == null ? null : profiles.none ? List.of() : profiles.names;
    Dashboard.Service published =
        yard.dashboard()
            .publish(
                service, scope, chosenProfiles, accessLog == null ? null : accessLog.equals("on"));
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
                : " Route Profiles: " + String.join(", ", published.profiles()) + ".")
            + (published.publication().accessLog() ? " Access log: on." : ""));
    out.flush();
    PrintWriter err = spec.commandLine().getErr();
    published.warnings().forEach(warning -> err.println(warning.line()));
    err.flush();
  }
}
