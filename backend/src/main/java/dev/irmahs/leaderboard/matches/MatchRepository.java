package dev.irmahs.leaderboard.matches;

import dev.irmahs.leaderboard.seasons.Season;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Integer> {

    List<Match> findBySeasonOrderByMatchNumber(Season season);
}
