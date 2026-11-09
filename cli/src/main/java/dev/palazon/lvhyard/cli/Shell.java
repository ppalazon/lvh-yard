package dev.palazon.lvhyard.cli;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** What lvh-yard takes from the process it runs in. The tests give their own. */
interface Shell {

  Map<String, String> environment();

  /** The standard input. */
  InputStream input();

  /** Whether a person can answer at a terminal. */
  boolean terminal();

  /**
   * Runs {@code editor} on {@code file} through {@code sh}, as $EDITOR can hold arguments such as
   * "code --wait", and waits until it ends.
   *
   * @return its exit code
   */
  int edit(String editor, Path file) throws IOException, InterruptedException;

  /** What {@code id <flag>} prints, such as {@code -u} for the user's ID, when it is a number. */
  Optional<String> id(String flag);

  /** The process that lvh-yard runs in. */
  static Shell system() {
    return new Shell() {
      @Override
      public Map<String, String> environment() {
        return System.getenv();
      }

      @Override
      public InputStream input() {
        return System.in;
      }

      @Override
      public boolean terminal() {
        Console console = System.console();
        return console != null && console.isTerminal();
      }

      @Override
      public int edit(String editor, Path file) throws IOException, InterruptedException {
        return new ProcessBuilder("sh", "-c", editor + " \"$1\"", "sh", file.toString())
            .inheritIO()
            .start()
            .waitFor();
      }

      @Override
      public Optional<String> id(String flag) {
        try {
          Process id = new ProcessBuilder("id", flag).redirectErrorStream(true).start();
          String printed = new String(id.getInputStream().readAllBytes()).strip();
          return id.waitFor() == 0 && printed.matches("\\d+")
              ? Optional.of(printed)
              : Optional.empty();
        } catch (IOException e) {
          return Optional.empty();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return Optional.empty();
        }
      }
    };
  }
}
