package cc.lvhy.yard.dashboard.consul;

/** Consul answered with a server error, such as for a datacenter that it has no path to. */
public class ConsulServerException extends ConsulException {

  public ConsulServerException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConsulServerException(String message) {
    super(message);
  }
}
