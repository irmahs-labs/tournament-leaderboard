package dev.irmahs.leaderboard.signups;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeasonRegistrationRepository extends JpaRepository<SeasonRegistration, SeasonRegistration.Key> {

    Optional<SeasonRegistration> findBySeasonIdAndPlayerId(Integer seasonId, UUID playerId);

    /** A season's sign-ups in the order they came. */
    List<SeasonRegistration> findBySeasonIdOrderByRegisteredAt(Integer seasonId);

    List<SeasonRegistration> findByPlayerId(UUID playerId);
}
