package dev.palazon.lvhyard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "unpublish",
    mixinStandardHelpOptions = true,
    description = "Unpublishes a Service.")
class Unpublish implements Runnable {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<service>", description = "The Service's name.")
  String service;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    yard.dashboard().unpublish(service);
    spec.commandLine()
        .getOut()
        .println("Unpublished " + service + ". Its Instances stay registered.");
    spec.commandLine().getOut().flush();
  }
}
