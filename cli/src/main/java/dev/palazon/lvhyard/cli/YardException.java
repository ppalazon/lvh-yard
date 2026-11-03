package dev.palazon.lvhyard.cli;

/** A failure that {@code lvh-yard} prints and exits with 1 for. */
class YardException extends RuntimeException {

  YardException(String message) {
    super(message);
  }
}
