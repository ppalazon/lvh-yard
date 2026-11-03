package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "alias",
    mixinStandardHelpOptions = true,
    subcommands = {Alias.Add.class, Alias.ListAliases.class, Alias.Remove.class},
    description = "Manages Aliases for Remote Services.")
class Alias implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  @Command(
      name = "add",
      mixinStandardHelpOptions = true,
      description = "Adds an Alias for a Remote Service.")
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
        description = "The Remote Service's name (default: <alias>).")
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

  @Command(name = "list", mixinStandardHelpOptions = true, description = "Lists the Aliases.")
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

  @Command(name = "remove", mixinStandardHelpOptions = true, description = "Removes an Alias.")
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
