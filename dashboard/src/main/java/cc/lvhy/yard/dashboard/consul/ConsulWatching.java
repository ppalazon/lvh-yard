package cc.lvhy.yard.dashboard.consul;

import java.time.Duration;

/** Blocking queries: waits for something in this datacenter to change. */
public interface ConsulWatching {

  /**
   * Waits until {@code watched} changes after {@code index}, or for {@code wait}, and answers its
   * index then. Index 0 answers at once.
   */
  long awaitChange(Watched watched, long index, Duration wait);

  /** What {@link #awaitChange} can wait for, in this datacenter. */
  enum Watched {
    /** The Health of every check. */
    HEALTH,
    /** Every key under {@code lvh-yard/}. */
    KV,
    /** The names of the Services in the catalog. */
    SERVICES
  }
}
