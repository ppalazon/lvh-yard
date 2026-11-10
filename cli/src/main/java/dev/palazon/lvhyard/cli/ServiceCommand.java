package dev.palazon.lvhyard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "service",
    mixinStandardHelpOptions = true,
    subcommands = {ServiceCommand.Create.class, ServiceCommand.Delete.class},
    description = "Creates or deletes a Service of this Workstation.")
class ServiceCommand implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  @Command(
      name = "create",
      mixinStandardHelpOptions = true,
      description = "Creates an empty, Unpublished Service, unless it exists.")
  static class Create implements Runnable {

    @ParentCommand ServiceCommand parent;

    @Parameters(paramLabel = "<service>", description = "The Service's name, a single DNS label.")
    String service;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      boolean created = parent.yard.dashboard().createService(service);
      spec.commandLine()
          .getOut()
          .println(
              created
                  ? "Created "
                      + service
                      + ". It is empty and Unpublished: attach Instances with lvh-yard register."
                  : service + " exists already.");
      spec.commandLine().getOut().flush();
    }
  }

  @Command(
      name = "delete",
      mixinStandardHelpOptions = true,
      description =
          "Deletes a Service with its Publication, Static Instances and Container Bindings.")
  static class Delete implements Runnable {

    @ParentCommand ServiceCommand parent;

    @Parameters(paramLabel = "<service>", description = "The Service's name.")
    String service;

    @Spec CommandSpec spec;

    @Override
    public void run() {
      parent.yard.dashboard().deleteService(service);
      spec.commandLine()
          .getOut()
          .println(
              "Deleted "
                  + service
                  + " with its Publication, Static Instances and Container Bindings. Its"
                  + " containers keep running.");
      spec.commandLine().getOut().flush();
    }
  }
}
