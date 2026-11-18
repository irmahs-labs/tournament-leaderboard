package dev.irmahs.leaderboard.presence;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * How many pages of the site are open right now: one stream per open page, held in memory and
 * never stored. Nothing identifies who has a page open, so there is no cookie and no record of
 * visitors.
 *
 * <p>A new page gets the count straight away. The other pages get the new count at most once a
 * second, so many pages opening together cost one message each rather than one per pair; and
 * every 25 seconds anyway, which keeps idle streams open through Cloudflare and drops the ones
 * whose page has closed.
 */
@Component
public class OnlineCount {

    /** Above this many open streams, new pages get no count rather than holding more memory. */
    static final int MAX_STREAMS = 5_000;

    static final String EVENT = "online";

    private final Set<SseEmitter> streams = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean changed = new AtomicBoolean();

    /**
     * A new open page: its stream, which receives the count straight away.
     *
     * @param timeoutMillis how long before the stream closes; the browser then opens another
     * @throws ResponseStatusException 503 when {@link #MAX_STREAMS} are already open
     */
    public SseEmitter join(long timeoutMillis) {
        if (streams.size() >= MAX_STREAMS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many open pages");
        }
        SseEmitter stream = new SseEmitter(timeoutMillis);
        stream.onCompletion(() -> leave(stream));
        stream.onTimeout(stream::complete);
        stream.onError(error -> leave(stream));
        streams.add(stream);
        changed.set(true);
        send(stream, streams.size());
        return stream;
    }

    public int count() {
        return streams.size();
    }

    /** Sends the count to every open page if it changed since the last time. */
    @Scheduled(fixedDelay = 1_000)
    public void sendChanges() {
        if (changed.getAndSet(false)) {
            sendToAll();
        }
    }

    /** Sends the count to every open page; a stream that can't take it is dropped. */
    @Scheduled(fixedRate = 25_000, initialDelay = 25_000)
    public void sendToAll() {
        int count = streams.size();
        for (SseEmitter stream : streams) {
            send(stream, count);
        }
    }

    private void send(SseEmitter stream, int count) {
        try {
            stream.send(SseEmitter.event().name(EVENT).data(count));
        } catch (IOException | IllegalStateException closed) {
            // The page went away: forget it; the next round sends the new count.
            if (streams.remove(stream)) {
                changed.set(true);
            }
        }
    }

    private void leave(SseEmitter stream) {
        if (streams.remove(stream)) {
            changed.set(true);
        }
    }
}
