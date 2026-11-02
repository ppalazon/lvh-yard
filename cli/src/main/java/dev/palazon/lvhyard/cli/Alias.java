package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code yard alias}: adds, lists and removes this Workstation's Aliases, short hostnames for
 * Remote Services that exist only here.
 */
@Command(
    name = "alias",
    mixinStandardHelpOptions = true,
    subcommands = {Alias.Add.class, Alias.ListAliases.class, Alias.Remove.class},
    description =
        "Adds, lists and removes Aliases: short hostnames for Remote Services, on this Workstation"
            + " only.")
class Alias implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  /** With no subcommand there is nothing to do but show what the command offers. */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  /**
   * {@code yard alias add}: gives a Remote Service an Alias, and says why it has no Route if it has
   * none.
   */
  @Command(
      name = "add",
      mixinStandardHelpOptions = true,
      description =
          "Gives a Remote Service the Alias <alias>.<base-domain>, or points the Alias there.")
  static class Add implements Runnable {

    @ParentCommand Alias alias;

    @Parameters(paramLabel = "<alias>", description = "The Alias's name.")
    String name;

    @Option(
        names = "--workstation",
        paramLabel = "<name>",
        required = true,
        description = "The Remote Service's Home Workstation.")
    String workstation;

    @Option(
        names = "--service",
        paramLabel = "<service>",
        description = "The Remote Service's name (default: the Alias's name).")
    String service;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      Dashboard.Alias added =
          alias.yard.dashboard().addAlias(name, workstation, service == null ? name : service);
      PrintWriter out = spec.commandLine().getOut();
      if (added.active()) {
        out.println(
            added.hostname()
                + " now reaches "
                + added.service()
                + " on "
                + added.workstation()
                + ".");
      } else {
        out.println(
            added.hostname()
                + " names "
                + added.service()
                + " on "
                + added.workstation()
                + ", but has no Route for now.");
      }
      out.flush();
      if (added.reason() != null) {
        spec.commandLine().getErr().println("Warning: " + added.reason());
        spec.commandLine().getErr().flush();
      }
    }
  }

  /**
   * {@code yard alias list}: every Alias, with the Remote Service it names, its state and why it
   * has no Route.
   */
  @Command(
      name = "list",
      mixinStandardHelpOptions = true,
      description =
          "Lists this Workstation's Aliases, with the Remote Service each names and its state.")
  static class ListAliases implements Runnable {

    @ParentCommand Alias alias;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      List<Dashboard.Alias> aliases = alias.yard.dashboard().aliases();
      PrintWriter out = spec.commandLine().getOut();
      if (aliases.isEmpty()) {
        out.println("No Aliases.");
      }
      for (Dashboard.Alias found : aliases) {
        out.println(
            found.hostname()
                + ": "
                + found.service()
                + " on "
                + found.workstation()
                + ", "
                + found.state());
        if (found.reason() != null) {
          out.println("  " + found.reason());
        }
      }
      out.flush();
    }
  }

  /** {@code yard alias remove}: removes an Alias, and its Route. */
  @Command(
      name = "remove",
      mixinStandardHelpOptions = true,
      description = "Removes an Alias, and its Route.")
  static class Remove implements Runnable {

    @ParentCommand Alias alias;

    @Parameters(paramLabel = "<alias>", description = "The Alias's name.")
    String name;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      alias.yard.dashboard().removeAlias(name);
      spec.commandLine().getOut().println("Removed the Alias " + name + ".");
      spec.commandLine().getOut().flush();
    }
  }
}
