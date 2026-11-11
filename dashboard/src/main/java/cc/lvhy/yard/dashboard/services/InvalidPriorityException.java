package cc.lvhy.yard.dashboard.services;

/** The message explains the rule to the person who gave the Instance Priority. */
public class InvalidPriorityException extends RuntimeException {

  InvalidPriorityException(String message) {
    super(message);
  }
}
