package dev.irmahs.leaderboard.seasons;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SeasonRepository extends JpaRepository<Season, Integer> {

    List<Season> findByOrganizationIdOrderByNumber(UUID organizationId);

    Optional<Season> findByOrganizationIdAndNumber(UUID organizationId, int number);
}
