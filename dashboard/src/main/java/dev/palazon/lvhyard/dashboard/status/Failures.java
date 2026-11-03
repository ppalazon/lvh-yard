package dev.palazon.lvhyard.dashboard.status;

/** How a part tells why a call failed. */
final class Failures {

  private Failures() {}

  /** The failure's message, or its kind when it has none, such as {@code ConnectException}. */
  static String message(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }
}
