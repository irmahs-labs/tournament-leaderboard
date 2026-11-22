package dev.irmahs.leaderboard.platform;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Which organisations can be deleted, and deleting one. An organisation can go while nothing in it
 * is worth keeping: no game was ever saved, and it has one admin at most, so deleting it takes
 * nothing from anyone but the person who asked for it. Through JDBC, child rows first, since
 * players, seasons and matches don't cascade from their organisation.
 */
@Component
class EmptyOrganizations {

    private final JdbcClient jdbc;

    EmptyOrganizations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The organisations that can't be deleted: they have a game, or more than one admin. */
    Set<UUID> kept() {
        return new HashSet<>(jdbc.sql("""
                        SELECT organization_id FROM matches m WHERE EXISTS (SELECT 1 FROM games g WHERE g.match_id = m.id)
                        UNION
                        SELECT organization_id FROM organization_members WHERE role = 'ADMIN'
                        GROUP BY organization_id HAVING count(*) > 1""").query(UUID.class).list());
    }

    boolean deletable(UUID organizationId) {
        return !kept().contains(organizationId);
    }

    /** Deletes the organisation with everything in it. */
    void delete(UUID organizationId) {
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
}
