package dev.palazon.lvhyard.dashboard.events;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Broadcasts Server-Sent Events to connected clients and maintains connections with heartbeats. */
@Component
public class EventBroadcaster implements SmartLifecycle {
  public static final String YARD_CHANGED = "yard-changed";
  public static final String CONTAINERS_CHANGED = "containers-changed";
  public static final String STATUS_CHANGED = "status-changed";

  private final Duration heartbeatInterval;
  private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
  private volatile Thread heartbeatThread;

  public EventBroadcaster() {
    this(Duration.ofSeconds(15));
  }

  @Autowired
  public EventBroadcaster(
      @Value("${lvh-yard.events.heartbeat-interval:15s}") Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  @Override
  public void start() {
    Thread starting = Thread.ofVirtual().name("sse-heartbeat").unstarted(this::heartbeatLoop);
    heartbeatThread = starting;
    starting.start();
  }

  @Override
  public void stop() {
    Thread stopping = heartbeatThread;
    heartbeatThread = null;
    if (stopping != null) {
      stopping.interrupt();
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.complete();
      } catch (Exception ignored) {
      }
    }
    emitters.clear();
  }

  @Override
  public boolean isRunning() {
    return heartbeatThread != null;
  }

  /** Registers a client connection that stays open until the client disconnects. */
  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);
    emitter.onCompletion(() -> remove(emitter));
    emitter.onTimeout(() -> remove(emitter));
    emitter.onError(error -> remove(emitter));
    return emitter;
  }

  /** Sends a named event signal to every connected client. */
  public void broadcast(String eventName) {
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(""));
      } catch (Exception e) {
        remove(emitter);
      }
    }
  }

  /** Sends a comment heartbeat to keep intermediate proxy connections alive. */
  public void sendHeartbeat() {
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().comment(" ping"));
      } catch (Exception e) {
        remove(emitter);
      }
    }
  }

  /** Returns the number of currently active client connections. */
  public int clientCount() {
    return emitters.size();
  }

  private void remove(SseEmitter emitter) {
    if (emitters.remove(emitter)) {
      try {
        emitter.complete();
      } catch (Exception ignored) {
      }
    }
  }

  private void heartbeatLoop() {
    while (heartbeatThread == Thread.currentThread()) {
      try {
        Thread.sleep(heartbeatInterval);
      } catch (InterruptedException e) {
        return;
      }
      sendHeartbeat();
    }
  }
}
