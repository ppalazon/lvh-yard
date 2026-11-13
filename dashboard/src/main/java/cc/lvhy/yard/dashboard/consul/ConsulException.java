package cc.lvhy.yard.dashboard.consul;

/**
 * Consul did not give the answer: it was silent, it failed, or it refused the request. The
 * subclasses name the two cases that mean that the datacenter cannot be read now.
 */
public class ConsulException extends RuntimeException {

  public ConsulException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConsulException(String message) {
    super(message);
  }
}
