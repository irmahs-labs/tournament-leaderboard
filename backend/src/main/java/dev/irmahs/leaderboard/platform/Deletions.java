package dev.irmahs.leaderboard.platform;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * What the platform admin deletes, through JDBC with child rows first: players, seasons and
 * matches don't cascade from their organisation, nor game lines from their player.
 */
@Component
class Deletions {

    private final JdbcClient jdbc;

    Deletions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** How many games each organisation has saved; one with none is left out. */
    Map<UUID, Long> gamesByOrganization() {
        Map<UUID, Long> games = new HashMap<>();
        jdbc.sql("""
                        SELECT m.organization_id, count(*) AS games FROM games g JOIN matches m ON m.id = g.match_id
                        GROUP BY m.organization_id""")
                .query((row, index) -> games.put(row.getObject(1, UUID.class), row.getLong(2)))
                .list();
        return games;
    }

    /** How many admins each organisation has; one with none is left out. */
    Map<UUID, Long> adminsByOrganization() {
        Map<UUID, Long> admins = new HashMap<>();
        jdbc.sql("""
                        SELECT organization_id, count(*) FROM organization_members WHERE role = 'ADMIN'
                        GROUP BY organization_id""")
                .query((row, index) -> admins.put(row.getObject(1, UUID.class), row.getLong(2)))
                .list();
        return admins;
    }

    /** Deletes the organisation with everything in it: games, players, seasons, members. */
    void deleteOrganization(UUID organizationId) {
        for (String sql : new String[] {
            "DELETE FROM matches WHERE organization_id = :org",
            "DELETE FROM seasons WHERE organization_id = :org",
            "DELETE FROM organization_members WHERE organization_id = :org",
            "DELETE FROM players WHERE organization_id = :org",
            "DELETE FROM organizations WHERE id = :org"
        }) {
            jdbc.sql(sql).param("org", organizationId).update();
        }
    }

    /** Whether a player has a line in any game. */
    boolean hasPlayed(UUID playerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM game_stats WHERE player_id = :player)")
                .param("player", playerId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Deletes a player who never played: their aliases, claims, sign-ups and answers go with them,
     * and an account linked to them stays a member, linked to nobody.
     */
    void deletePlayer(UUID playerId) {
        jdbc.sql("UPDATE organization_members SET player_id = NULL WHERE player_id = :player")
                .param("player", playerId)
                .update();
        jdbc.sql("DELETE FROM players WHERE id = :player")
                .param("player", playerId)
                .update();
    }
}
