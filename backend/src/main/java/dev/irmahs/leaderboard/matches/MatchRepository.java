package dev.irmahs.leaderboard.matches;

import dev.irmahs.leaderboard.seasons.Season;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MatchRepository extends JpaRepository<Match, Integer> {

    List<Match> findBySeasonOrderByMatchNumber(Season season);

    Optional<Match> findBySeasonAndMatchNumber(Season season, int matchNumber);

    Optional<Match> findByIdAndOrganizationId(Integer id, UUID organizationId);

    /**
     * The match, its row locked until the transaction ends: every change to a roster goes through
     * this, so two players taking the last place are handled one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select match from Match match where match.id = :id and match.organizationId = :organizationId")
    Optional<Match> lockById(Integer id, UUID organizationId);

    /** The matches of these seasons, in order. */
    List<Match> findBySeasonInOrderBySeasonNumberDescMatchNumber(Collection<Season> seasons);
}
