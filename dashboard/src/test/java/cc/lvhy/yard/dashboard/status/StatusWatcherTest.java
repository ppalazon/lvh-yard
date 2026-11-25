package cc.lvhy.yard.dashboard.status;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.STATUS_CHANGED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.lvhy.yard.dashboard.events.EventDebouncer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** When the watcher signals a change of the status: only when a part that it knew changes state. */
class StatusWatcherTest {

  private final StatusReader reader = mock(StatusReader.class);
  private final EventDebouncer debouncer = mock(EventDebouncer.class);
  private final StatusWatcher watcher = new StatusWatcher(reader, debouncer, Duration.ofDays(1));

  private static Status status(Part... parts) {
    return Status.of(List.of(parts));
  }

  private static Part part(String name, PartState state) {
    return new Part(name, state, "", List.of(), List.of());
  }

  private void read(Status status) {
    when(reader.status()).thenReturn(status);
    watcher.checkStatus();
  }

  @Test
  void firstReadSignalsNothing() {
    read(status(part("Traefik", PartState.FAILING)));

    verify(debouncer, never()).trigger(STATUS_CHANGED);
  }

  @Test
  void partThatKeepsItsStateSignalsNothing() {
    read(status(part("Traefik", PartState.OK)));
    read(status(part("Traefik", PartState.OK)));

    verify(debouncer, never()).trigger(STATUS_CHANGED);
  }

  @Test
  void partThatChangesStateSignalsOnce() {
    read(status(part("Consul", PartState.OK), part("Traefik", PartState.FAILING)));
    read(status(part("Consul", PartState.OK), part("Traefik", PartState.OK)));

    verify(debouncer, times(1)).trigger(STATUS_CHANGED);
  }

  @Test
  void eachChangeOfStateSignals() {
    read(status(part("Traefik", PartState.FAILING)));
    read(status(part("Traefik", PartState.WARNING)));
    read(status(part("Traefik", PartState.OK)));

    verify(debouncer, times(2)).trigger(STATUS_CHANGED);
  }

  @Test
  void partThatAppearsLaterSignalsNothingUntilItChanges() {
    read(status(part("Consul", PartState.OK)));
    read(status(part("Consul", PartState.OK), part("Traefik", PartState.FAILING)));

    verify(debouncer, never()).trigger(STATUS_CHANGED);

    read(status(part("Consul", PartState.OK), part("Traefik", PartState.OK)));

    verify(debouncer, times(1)).trigger(STATUS_CHANGED);
  }
}
