package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import picocli.AutoComplete;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "completions",
    hidden = true,
    mixinStandardHelpOptions = true,
    description = "Prints a shell completion script.")
class Completions implements Runnable {

  enum Shell {
    bash,
    zsh
  }

  @Parameters(paramLabel = "<shell>", description = "The shell: ${COMPLETION-CANDIDATES}.")
  Shell shell;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    PrintWriter out = spec.commandLine().getOut();
    CommandSpec root = spec.root();
    // Picocli's script loads bashcompinit when Zsh sources it.
    out.print(AutoComplete.bash(root.name(), root.commandLine()));
    out.flush();
  }
}
