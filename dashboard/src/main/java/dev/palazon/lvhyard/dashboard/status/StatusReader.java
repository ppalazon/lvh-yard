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
import org.springframework.stereotype.Component;

/**
 * Reads every {@link StatusPart} side by side, each for at most {@link #PART_TIMEOUT}, and keeps
 * the status for {@link #KEPT}, so that many open pages cost one round of calls. A part that
 * throws, or does not answer in time, is failing; it never holds up the others for longer.
 */
@Component
public class StatusReader {

  /** How long one part may take. Past it, the part is failing and its read is abandoned. */
  static final Duration PART_TIMEOUT = Duration.ofSeconds(2);

  /** How long a status is answered again before the parts are read anew. */
  static final Duration KEPT = Duration.ofSeconds(5);

  private final List<StatusPart> parts;

  /** Reads run on their own threads, so one that never answers is simply left behind. */
  private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();

  private Status last;

  private Instant lastRead = Instant.MIN;

  StatusReader(List<StatusPart> parts) {
    this.parts = List.copyOf(parts);
  }

  @PreDestroy
  void close() {
    readers.shutdownNow();
  }

  /**
   * The status of every part: the one read less than {@link #KEPT} ago, else one read now. Callers
   * that ask while it is read wait for that same read.
   */
  public synchronized Status status() {
    Instant now = Instant.now();
    if (last == null || !now.isBefore(lastRead.plus(KEPT))) {
      last = readNow();
      lastRead = Instant.now();
    }
    return last;
  }

  private Status readNow() {
    List<Future<Part>> reads = new ArrayList<>();
    for (StatusPart part : parts) {
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
  private static Part await(StatusPart part, Future<Part> reading, long deadline) {
    try {
      return reading.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      reading.cancel(true);
      return Part.failing(
          part.name(), part.name() + " did not answer within " + PART_TIMEOUT.toSeconds() + " s.");
    } catch (ExecutionException e) {
      return Part.failing(part.name(), part.name() + " could not be read: " + reason(e.getCause()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Part.failing(part.name(), "Interrupted while " + part.name() + " was read.");
    }
  }

  private static String reason(Throwable failure) {
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }
}
