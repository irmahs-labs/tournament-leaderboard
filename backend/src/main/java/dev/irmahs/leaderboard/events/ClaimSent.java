package dev.irmahs.leaderboard.events;

import java.util.UUID;

/** A member asked to be linked to a player profile. */
public record ClaimSent(UUID organizationId, UUID claimantId, String riotId) {}
