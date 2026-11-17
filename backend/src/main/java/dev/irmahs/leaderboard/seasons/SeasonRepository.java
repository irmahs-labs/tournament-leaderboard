package dev.irmahs.leaderboard.seasons;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SeasonRepository extends JpaRepository<Season, Integer> {

    List<Season> findByOrganizationIdOrderByNumber(UUID organizationId);

    Optional<Season> findByOrganizationIdAndNumber(UUID organizationId, int number);

    Optional<Season> findFirstByOrganizationIdOrderByNumberDesc(UUID organizationId);

    /** The season's highest match number that has a match, or nothing. */
    @Query("select max(match.matchNumber) from Match match where match.season = :season")
    Integer highestMatchNumber(Season season);

    /** The highest game number in any of the season's matches, or nothing. */
    @Query("select max(game.gameNumber) from Game game where game.match.season = :season")
    Integer highestGameNumber(Season season);
}
