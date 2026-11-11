package cc.lvhy.yard.dashboard.services;

/** The message explains the rule to the person who asked. */
public class InvalidRegistrationException extends RuntimeException {

  private final String field;

  public InvalidRegistrationException(String field, String message) {
    super(message);
    this.field = field;
  }

  /** Such as {@code name}, {@code port} or {@code containerPort}. */
  public String field() {
    return field;
  }
}
