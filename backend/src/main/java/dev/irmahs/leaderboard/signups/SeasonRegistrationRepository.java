package dev.irmahs.leaderboard.signups;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeasonRegistrationRepository extends JpaRepository<SeasonRegistration, SeasonRegistration.Key> {

    Optional<SeasonRegistration> findBySeasonIdAndPlayerId(Integer seasonId, UUID playerId);

    /** Who's signed up for a season, in the order they came. */
    List<SeasonRegistration> findBySeasonIdAndStatusOrderByRegisteredAt(Integer seasonId, RegistrationStatus status);

    List<SeasonRegistration> findByPlayerIdAndStatus(UUID playerId, RegistrationStatus status);
}
