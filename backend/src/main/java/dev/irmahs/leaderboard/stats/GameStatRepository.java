package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.seasons.Season;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface GameStatRepository extends JpaRepository<GameStat, Integer> {

    /** Every line of a season, with its player, game, match and bonuses already loaded. */
    @Query("""
            select distinct line from GameStat line
            join fetch line.player
            join fetch line.game game
            join fetch game.match match
            left join fetch line.bonuses
            where match.season = :season
            """)
    List<GameStat> findBySeason(Season season);
}
