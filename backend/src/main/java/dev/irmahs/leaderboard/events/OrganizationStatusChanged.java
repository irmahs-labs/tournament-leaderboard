package dev.irmahs.leaderboard.events;

import java.util.UUID;

/** The platform admin suspended an organisation, or reactivated it. */
public record OrganizationStatusChanged(UUID organizationId, boolean suspended) {}
