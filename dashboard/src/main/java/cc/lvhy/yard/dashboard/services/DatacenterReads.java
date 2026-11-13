package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.ConsulServerException;
import cc.lvhy.yard.dashboard.consul.ConsulSettings;
import cc.lvhy.yard.dashboard.consul.ConsulUnreachableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Reads the datacenter of a Workstation, and gives a Workstation that does not answer as {@link
 * UnreachableWorkstationException}.
 */
@Component
class DatacenterReads {

  private final Workstation workstation;

  /**
   * When another Workstation is not asked: after two reads in a row found it unreachable, for the
   * unreachable pause. Then one read tries it again, and the Workstation shows again by itself once
   * that read succeeds. So a Workstation that is switched off does not make every load wait for
   * Consul's timeout.
   */
  private final CircuitBreakerRegistry breakers;

  DatacenterReads(Workstation workstation, ConsulSettings consulSettings) {
    this.workstation = workstation;
    this.breakers =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(consulSettings.unreachablePause())
                .permittedNumberOfCallsInHalfOpenState(1)
                // Any other error means the datacenter answered: a fault, not an unreachable
                // Workstation.
                .recordExceptions(ConsulUnreachableException.class, ConsulServerException.class)
                .build());
  }

  /**
   * What {@code read} gives for {@code workstation}'s datacenter: {@code null}, the agent's own,
   * for this Workstation. Another Workstation's datacenter that does not answer in time, or that
   * Consul has no path to (a 5xx), makes it unreachable, and so does a Workstation that is not
   * asked for now.
   */
  <T> T read(String workstation, Function<String, T> read) {
    if (this.workstation.is(workstation)) {
      return read.apply(null);
    }
    try {
      return breakers.circuitBreaker(workstation).executeSupplier(() -> read.apply(workstation));
    } catch (CallNotPermittedException | ConsulUnreachableException | ConsulServerException e) {
      throw new UnreachableWorkstationException(workstation, e);
    }
  }
}
