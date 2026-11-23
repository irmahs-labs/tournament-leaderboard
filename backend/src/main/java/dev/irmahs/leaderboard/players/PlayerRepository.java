package dev.irmahs.leaderboard.players;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Player> findByOrganizationIdAndRiotIdIgnoreCase(UUID organizationId, String riotId);

    Optional<Player> findByOrganizationIdAndExpectedEmailIgnoreCase(UUID organizationId, String email);

    List<Player> findByOrganizationIdOrderByRiotId(UUID organizationId);

    /** The players, in any organisation, waiting for someone to sign in with this email. */
    List<Player> findByExpectedEmailIgnoreCase(String email);

    /**
     * The organisation's players whose Riot ID, Discord username or an alias contains the
     * pattern, by Riot ID.
     *
     * @param pattern a lowercase LIKE pattern, with backslash as its escape character
     */
    @Query("""
            select player from Player player
            where player.organizationId = :organizationId
            and (lower(player.riotId) like :pattern escape '\\'
                 or lower(player.discordUsername) like :pattern escape '\\'
                 or exists (select 1 from PlayerAlias alias
                            where alias.player = player and lower(alias.alias) like :pattern escape '\\'))
            order by lower(player.riotId)
            """)
    List<Player> search(UUID organizationId, String pattern);

    /** The organisation's players with a line in a game. */
    @Query(value = """
            SELECT DISTINCT gs.player_id FROM game_stats gs JOIN players p ON p.id = gs.player_id
            WHERE p.organization_id = :organizationId""", nativeQuery = true)
    List<UUID> playedIn(UUID organizationId);
}
