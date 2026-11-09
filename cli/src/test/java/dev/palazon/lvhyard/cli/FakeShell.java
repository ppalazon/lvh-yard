package dev.palazon.lvhyard.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The process that a unit test gives lvh-yard: its environment, its standard input, and an editor
 * that is a function of the file's text.
 */
record FakeShell(Map<String, String> environment, String typed, UnaryOperator<String> editor)
    implements Shell {

  FakeShell(Map<String, String> environment) {
    this(environment, "", text -> text);
  }

  @Override
  public InputStream input() {
    return new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8));
  }

  /** No person answers: a test that types answers gives {@code --terminal}. */
  @Override
  public boolean terminal() {
    return false;
  }

  @Override
  public int edit(String command, Path file) throws IOException {
    Files.writeString(file, editor.apply(Files.readString(file)));
    return 0;
  }

  /** The IDs of the user that runs the tests: those that own a new file. */
  @Override
  public Optional<String> id(String flag) {
    try {
      Path file = Files.createTempFile("id", ".txt");
      try {
        return Optional.of(
            String.valueOf(Files.getAttribute(file, flag.equals("-u") ? "unix:uid" : "unix:gid")));
      } finally {
        Files.delete(file);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
