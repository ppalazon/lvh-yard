package dev.palazon.lvhyard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "static",
    mixinStandardHelpOptions = true,
    description = "Registers a Static Instance, and publishes a new Service.")
class RegisterStatic implements Runnable {

  @ParentCommand Register register;

  @Parameters(paramLabel = "<service>", description = "The Service's name, a single DNS label.")
  String service;

  @Option(
      names = "--port",
      paramLabel = "<port>",
      required = true,
      description = "The port the Instance listens on.")
  int port;

  @Mixin StaticInstanceOptions options;

  @Option(names = "--no-publish", description = "Registers a new Service without publishing it.")
  boolean noPublish;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    Dashboard.Instance instance =
        register
            .dashboard()
            .register(
                service,
                options.host,
                port,
                options.healthCheck(),
                options.tags,
                options.meta,
                Register.publish(noPublish));
    spec.commandLine()
        .getOut()
        .println(
            "Registered "
                + service
                + " at "
                + instance.address()
                + ", Instance "
                + instance.id()
                + ".");
    spec.commandLine().getOut().flush();
  }
}
