package dev.irmahs.leaderboard.events;

import java.util.UUID;

/**
 * An admin accepted or declined a claim.
 *
 * @param reason why it was declined; nothing when accepted or none was given
 */
public record ClaimAnswered(UUID organizationId, UUID claimantId, String riotId, boolean accepted, String reason) {}
