package dev.palazon.lvhyard.cli;

import java.net.URI;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The {@code yard} command. It talks only to the dashboard on its own Workstation, through the REST
 * API that {@code openapi.yaml} describes; it never talks to Consul.
 */
@Command(
    name = "yard",
    mixinStandardHelpOptions = true,
    subcommands = {
      ListServices.class,
      Register.class,
      Remove.class,
      Publish.class,
      Unpublish.class,
      Alias.class,
      TestReachability.class
    },
    description = "Registers, publishes and tests Services on this Workstation's LVH Yard.")
public class Yard implements Runnable {

  /** Where {@code mise run dashboard} serves the dashboard. */
  private static final String LOOPBACK_DASHBOARD = "http://127.0.0.1:8780";

  @Option(
      names = "--dashboard",
      paramLabel = "<url>",
      defaultValue = "${env:YARD_DASHBOARD_URL:-" + LOOPBACK_DASHBOARD + "}",
      description = {
        "The dashboard on this Workstation.",
        "Default: YARD_DASHBOARD_URL when set,",
        "else " + LOOPBACK_DASHBOARD + "."
      })
  URI dashboard;

  @Spec CommandSpec spec;

  /** With no subcommand there is nothing to do but show what the command offers. */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  /** The dashboard that {@code --dashboard} names. */
  Dashboard dashboard() {
    return new Dashboard(dashboard);
  }

  /**
   * The command line, ready to execute: a failure to talk to the dashboard is printed and exits
   * with 1.
   */
  static CommandLine commandLine() {
    CommandLine yard =
        new CommandLine(new Yard())
            .setExecutionExceptionHandler((exception, command, parseResult) -> {
              if (!(exception instanceof YardException)) {
                throw exception;
              }
              command.getErr().println(exception.getMessage());
              command.getErr().flush();
              return 1;
            });
    withVersion(yard);
    return yard;
  }

  /** {@code command} and each of its subcommands print this build's version for -V. */
  private static void withVersion(CommandLine command) {
    command.getCommandSpec().version("yard " + Version.VERSION);
    command.getSubcommands().values().forEach(Yard::withVersion);
  }

  public static void main(String[] args) {
    System.exit(commandLine().execute(args));
  }
}
