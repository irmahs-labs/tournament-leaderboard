package dev.irmahs.leaderboard.events;

import java.util.Map;
import java.util.UUID;

/**
 * An admin changed something the organisation's other admins should hear about, such as a game's
 * scores or a season. Published by the signed-in admin's request; whoever made the change isn't
 * told.
 *
 * @param change what happened, such as {@code GAME_SAVED}
 * @param details what it's about, shown in the notice, such as the season and match numbers
 */
public record AdminChanged(UUID organizationId, String change, Map<String, Object> details) {}
