package dev.irmahs.leaderboard.events;

import java.util.UUID;

/**
 * An admin planned a match, changed when or where it's played, called it off or put it back on.
 *
 * @param change PLANNED, MOVED or CANCELLED
 */
public record MatchChanged(UUID organizationId, int matchId, String change) {}
