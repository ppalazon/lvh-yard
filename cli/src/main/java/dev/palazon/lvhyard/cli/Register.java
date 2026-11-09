package dev.palazon.lvhyard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "register",
    mixinStandardHelpOptions = true,
    subcommands = {RegisterStatic.class, RegisterContainer.class, RegisterCompose.class},
    description =
        "Registers a Static Instance, or binds a container or Compose service, in an existing"
            + " Service.")
class Register implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  Dashboard dashboard() {
    return yard.dashboard();
  }
}
