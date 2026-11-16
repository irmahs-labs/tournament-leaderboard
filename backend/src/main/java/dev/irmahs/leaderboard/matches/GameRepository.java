package dev.irmahs.leaderboard.matches;

import dev.irmahs.leaderboard.seasons.Season;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, Integer> {

    Optional<Game> findByMatchAndGameNumber(Match match, int gameNumber);

    long countByMatch(Match match);

    /** The season's saved games that come before this one, the latest first. */
    @Query("""
            select game from Game game join game.match match
            where match.season = :season
            and (match.matchNumber < :matchNumber
                 or (match.matchNumber = :matchNumber and game.gameNumber < :gameNumber))
            and exists (select 1 from GameStat line where line.game = game)
            order by match.matchNumber desc, game.gameNumber desc
            """)
    List<Game> findSavedBefore(Season season, int matchNumber, int gameNumber);
}
