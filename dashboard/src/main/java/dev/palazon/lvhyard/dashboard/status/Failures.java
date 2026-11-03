package dev.palazon.lvhyard.dashboard.status;

final class Failures {

  private Failures() {}

  /** Its kind when it has no message, such as {@code ConnectException}. */
  static String message(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }
}
