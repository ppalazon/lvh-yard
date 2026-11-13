package cc.lvhy.yard.dashboard.consul;

import java.util.Map;

/** The key-value store of a datacenter. */
public interface ConsulKv {

  /** Consul's own limit on the operations of one transaction. */
  int TRANSACTION_SIZE = 64;

  void putValue(String key, String value);

  /**
   * Writes each value at its key in one Consul transaction, so Consul takes all or none.
   *
   * @param values at most {@link #TRANSACTION_SIZE}
   */
  void putValues(Map<String, String> values);

  Map<String, String> values(String datacenter, String prefix);

  /** A key that is not there is no error. */
  void deleteValue(String key);
}
