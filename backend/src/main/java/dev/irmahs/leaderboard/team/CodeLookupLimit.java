package dev.irmahs.leaderboard.team;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * At most 10 code lookups per minute from one address, which makes guessing codes pointless. Kept
 * in memory only; a restart forgets it.
 */
@Component
class CodeLookupLimit {

    static final int PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    /** Beyond this many addresses, the oldest entries are dropped, so memory stays small. */
    private static final int MAX_ADDRESSES = 10_000;

    private final Map<String, Deque<Instant>> lookups = new HashMap<>();
    private final Clock clock;

    CodeLookupLimit() {
        this(Clock.systemUTC());
    }

    CodeLookupLimit(Clock clock) {
        this.clock = clock;
    }

    /** Counts one lookup from this address; false when it's over the limit. */
    synchronized boolean allow(String address) {
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW);
        if (lookups.size() >= MAX_ADDRESSES) {
            lookups.values()
                    .removeIf(times -> times.isEmpty() || !times.peekLast().isAfter(since));
            if (lookups.size() >= MAX_ADDRESSES) {
                lookups.clear();
            }
        }
        Deque<Instant> times = lookups.computeIfAbsent(address, key -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(since)) {
            times.pollFirst();
        }
        if (times.size() >= PER_MINUTE) {
            return false;
        }
        times.addLast(now);
        return true;
    }
}
