package dev.irmahs.leaderboard.rosters;

import java.util.List;
import java.util.UUID;

/** Players signed up for a match who haven't answered are reminded to, by an admin or two days before the reply-by date. */
public record Reminded(UUID organizationId, int matchId, List<UUID> players) {}
