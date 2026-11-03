package dev.palazon.lvhyard.dashboard.status;

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
 * Reads every {@link PartReader} side by side, each for at most {@link #PART_TIMEOUT}, and keeps
 * the status for {@link #KEPT}, so that many open pages cost one round of calls. A part that
 * throws, or does not answer in time, is failing; it never holds up the others for longer. Once
 * kept too long, the status is still answered at once while it is read again in the background:
 * only the very first status waits for its parts.
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

  /** The last status read, and when; none before the first. */
  private volatile Read last;

  /** Whether the status is being read again in the background. */
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

  /**
   * The status of every part, the last one read. When it is older than {@link #KEPT}, it is read
   * again in the background, and the next callers get the new one. The first caller, with no status
   * read yet, waits for it, and so do those who ask meanwhile.
   */
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

  /** A status, and when it was read. */
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

  /** What {@code reading} of {@code part} found by {@code deadline}, or why it is failing. */
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
