package dev.irmahs.leaderboard.members;

import java.util.UUID;

/** How many members an organisation has. */
public record MemberCount(UUID organizationId, long members) {}
