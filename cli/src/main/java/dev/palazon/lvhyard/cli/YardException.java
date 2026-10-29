package dev.palazon.lvhyard.cli;

/**
 * A failure {@code yard} explains to the person at the terminal, and exits with 1 for: the dashboard's refusal, a
 * dashboard that does not answer, or an Instance that cannot be found.
 */
class YardException extends RuntimeException {

    YardException(String message) {
        super(message);
    }
}
