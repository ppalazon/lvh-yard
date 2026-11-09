package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "alias",
    mixinStandardHelpOptions = true,
    subcommands = {Alias.Add.class, Alias.ListAliases.class, Alias.Remove.class},
    description = "Manages the Aliases of a Service, which give it a Remote Service's Instances.")
class Alias implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  /** The Remote Service that an Alias names. */
  static class Target {

    @Option(
        names = "--workstation",
        paramLabel = "<name>",
        required = true,
        description = "The Home Workstation of the Remote Service.")
    String workstation;

    @Option(
        names = "--service",
        paramLabel = "<service>",
        required = true,
        description = "The name of the Remote Service.")
    String service;
  }

  @Command(
      name = "add",
      mixinStandardHelpOptions = true,
      description = {
        "Adds an Alias to a Remote Service with Yard Scope, at the end of the order.",
        "If the Service does not exist, creates it and publishes it with Local Scope."
      })
  static class Add implements Runnable {

    @ParentCommand Alias alias;

    @Parameters(paramLabel = "<service>", description = "The Service of this Workstation.")
    String service;

    @Mixin Target target;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      Dashboard.Alias added =
          alias.yard.dashboard().addAlias(service, target.workstation, target.service);
      PrintWriter out = spec.commandLine().getOut();
      out.println(
          service + " now has an Alias to " + added.service() + " on " + added.workstation() + ".");
      out.flush();
      if (added.reason() != null) {
        spec.commandLine().getErr().println("Warning: " + added.reason());
        spec.commandLine().getErr().flush();
      }
    }
  }

  @Command(
      name = "list",
      mixinStandardHelpOptions = true,
      description = "Lists the Aliases of a Service in order, with their state.")
  static class ListAliases implements Runnable {

    @ParentCommand Alias alias;

    @Parameters(paramLabel = "<service>", description = "The Service of this Workstation.")
    String service;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      List<Dashboard.Alias> aliases = alias.yard.dashboard().aliases(service);
      PrintWriter out = spec.commandLine().getOut();
      if (aliases.isEmpty()) {
        out.println(service + " has no Aliases.");
      }
      for (Dashboard.Alias found : aliases) {
        out.println(found.service() + " on " + found.workstation() + ": " + found.state());
        if (found.reason() != null) {
          out.println("  " + found.reason());
        }
      }
      out.flush();
    }
  }

  @Command(
      name = "remove",
      mixinStandardHelpOptions = true,
      description = "Removes one Alias of a Service.")
  static class Remove implements Runnable {

    @ParentCommand Alias alias;

    @Parameters(paramLabel = "<service>", description = "The Service of this Workstation.")
    String service;

    @Mixin Target target;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      alias.yard.dashboard().removeAlias(service, target.workstation, target.service);
      spec.commandLine()
          .getOut()
          .println(
              "Removed the Alias to "
                  + target.service
                  + " on "
                  + target.workstation
                  + " from "
                  + service
                  + ".");
      spec.commandLine().getOut().flush();
    }
  }
}
