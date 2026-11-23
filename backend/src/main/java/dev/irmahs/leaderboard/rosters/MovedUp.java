package dev.irmahs.leaderboard.rosters;

import java.util.UUID;

/** A player waiting took a place that freed up, published once the change is saved. */
public record MovedUp(UUID organizationId, int matchId, UUID playerId) {}
