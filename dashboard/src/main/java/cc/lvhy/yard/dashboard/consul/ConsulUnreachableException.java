package cc.lvhy.yard.dashboard.consul;

/** Consul did not answer in time, or could not be connected to. */
public class ConsulUnreachableException extends ConsulException {

  public ConsulUnreachableException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConsulUnreachableException(String message) {
    super(message);
  }
}
