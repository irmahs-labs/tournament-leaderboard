package dev.irmahs.leaderboard.presence;

import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The count of open pages shown in every header, as a stream of server-sent events. */
@RestController
class OnlineController {

    /** Long enough to cost nothing, short enough that no stream is held forever. */
    static final Duration STREAM_LIFETIME = Duration.ofMinutes(30);

    private final OnlineCount online;

    OnlineController(OnlineCount online) {
        this.online = online;
    }

    /** {@code event: online} with the number of open pages, on every change. */
    @GetMapping(path = "/api/online", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter online() {
        return online.join(STREAM_LIFETIME.toMillis());
    }
}
