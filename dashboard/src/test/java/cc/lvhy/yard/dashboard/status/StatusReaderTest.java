package cc.lvhy.yard.dashboard.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How the status is read and kept, with parts that the test makes slow at will. */
class StatusReaderTest {

  @Test
  void aStaleStatusIsAnsweredAtOnceWhileItIsReadAgain() {
    SlowPart part = new SlowPart();
    StatusReader reader = new StatusReader(List.of(part), Duration.ZERO);
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

  /** A part that answers {@code read <n>} for its n-th read, after {@link #delay}. */
  private static final class SlowPart implements PartReader {

    volatile Duration delay = Duration.ZERO;

    private int reads;

    @Override
    public String name() {
      return "Slow";
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
