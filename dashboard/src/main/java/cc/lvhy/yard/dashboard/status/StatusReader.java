package cc.lvhy.yard.dashboard.status;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Keeps the status for {@link #KEPT}, so many open pages cost one round of calls. A part that
 * throws or takes too long is failing, and never holds up the others. A kept status is answered at
 * once while it is read again in the background, so only the first status waits.
 */
@Component
public class StatusReader {

  /** How long one part may take. Past it, the part is failing and its read is abandoned. */
  static final Duration PART_TIMEOUT = Duration.ofSeconds(2);

  /** How long a status is answered before the parts are read anew. */
  static final Duration KEPT = Duration.ofSeconds(5);

  private final List<PartReader> parts;

  private final Duration kept;

  /** Reads run on their own threads, so one that never answers is simply left behind. */
  private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();

  private volatile Read last;

  private final AtomicBoolean rereading = new AtomicBoolean();

  @Autowired
  StatusReader(List<PartReader> parts) {
    this(parts, KEPT);
  }

  StatusReader(List<PartReader> parts, Duration kept) {
    this.parts = List.copyOf(parts);
    this.kept = kept;
  }

  @PreDestroy
  void close() {
    readers.shutdownNow();
  }

  public Status status() {
    Read read = last;
    if (read == null) {
      return first();
    }
    if (!Instant.now().isBefore(read.at().plus(kept)) && rereading.compareAndSet(false, true)) {
      readers.execute(() -> {
        try {
          last = new Read(readNow(), Instant.now());
        } finally {
          rereading.set(false);
        }
      });
    }
    return read.status();
  }

  private synchronized Status first() {
    if (last == null) {
      last = new Read(readNow(), Instant.now());
    }
    return last.status();
  }

  private record Read(Status status, Instant at) {}

  private Status readNow() {
    List<Future<Part>> reads = new ArrayList<>();
    for (PartReader part : parts) {
      reads.add(readers.submit(part::read));
    }
    long deadline = System.nanoTime() + PART_TIMEOUT.toNanos();
    List<Part> read = new ArrayList<>();
    for (int i = 0; i < parts.size(); i++) {
      read.add(await(parts.get(i), reads.get(i), deadline));
    }
    return Status.of(read);
  }

  private static Part await(PartReader part, Future<Part> reading, long deadline) {
    try {
      return reading.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      reading.cancel(true);
      return Part.failing(
          part.name(), part.name() + " did not answer within " + PART_TIMEOUT.toSeconds() + " s.");
    } catch (ExecutionException e) {
      return Part.failing(
          part.name(), part.name() + " could not be read: " + Failures.message(e.getCause()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Part.failing(part.name(), "Interrupted while " + part.name() + " was read.");
    }
  }
}
