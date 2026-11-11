package cc.lvhy.yard.dashboard.events;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Streams Server-Sent Events to connected browser tabs. */
@RestController
class EventsController {

  private final EventBroadcaster broadcaster;

  EventsController(EventBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events() {
    return broadcaster.subscribe();
  }
}
