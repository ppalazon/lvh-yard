package cc.lvhy.yard.dashboard.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How the status is read and kept, with parts that the test makes slow at will. */
class StatusReaderTest {

  @Test
  void aStaleStatusIsAnsweredAtOnceWhileItIsReadAgain() {
    SlowPart part = new SlowPart();
    StatusReader reader = new StatusReader(List.of(part), Duration.ZERO, Clock.systemUTC());
    try {
      assertThat(reader.status().parts().getFirst().reason()).isEqualTo("read 1");

      part.delay = Duration.ofMillis(1500);
      Instant asked = Instant.now();
      Status stale = reader.status();

      assertThat(Duration.between(asked, Instant.now())).isLessThan(Duration.ofMillis(500));
      assertThat(stale.parts().getFirst().reason()).isEqualTo("read 1");
      await()
          .atMost(Duration.ofSeconds(5))
          .until(() -> reader.status().parts().getFirst().reason(), "read 2"::equals);
    } finally {
      reader.close();
    }
  }

  @Test
  void aStatusIsKeptUntilItsTimeIsUp() {
    SlowPart part = new SlowPart();
    SettableClock clock = new SettableClock();
    StatusReader reader = new StatusReader(List.of(part), Duration.ofSeconds(5), clock);
    try {
      assertThat(reader.status().parts().getFirst().reason()).isEqualTo("read 1");

      clock.advance(Duration.ofSeconds(4));
      assertThat(reader.status().parts().getFirst().reason()).isEqualTo("read 1");
      assertThat(part.reads()).as("the part is not read again within the kept time").isEqualTo(1);

      clock.advance(Duration.ofSeconds(1));
      reader.status();
      await().atMost(Duration.ofSeconds(5)).until(part::reads, reads -> reads == 2);
    } finally {
      reader.close();
    }
  }

  /** A clock that moves only when the test says so. */
  private static final class SettableClock extends Clock {

    private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }
  }

  /** A part that answers {@code read <n>} for its n-th read, after {@link #delay}. */
  private static final class SlowPart implements PartReader {

    volatile Duration delay = Duration.ZERO;

    private int reads;

    @Override
    public String name() {
      return "Slow";
    }

    synchronized int reads() {
      return reads;
    }

    @Override
    public synchronized Part read() {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      reads++;
      return Part.of(name(), "read " + reads, List.of());
    }
  }
}
