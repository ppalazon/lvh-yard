package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/** The {@code yard} command as a shell runs it: arguments in, exit code and output out. */
class YardTest {

    @Test
    void withoutSubcommandShowsUsage() {
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Yard());
        command.setOut(new PrintWriter(out));

        int exitCode = command.execute();

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Usage: yard").contains("--dashboard=<url>");
    }
}
