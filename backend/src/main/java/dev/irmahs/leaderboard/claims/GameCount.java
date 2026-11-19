package dev.irmahs.leaderboard.claims;

import java.util.UUID;

/** How many games a player has played. */
public record GameCount(UUID playerId, long games) {}
