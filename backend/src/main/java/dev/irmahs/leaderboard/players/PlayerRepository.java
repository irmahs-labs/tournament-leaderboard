package dev.irmahs.leaderboard.players;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Player> findByOrganizationIdAndRiotIdIgnoreCase(UUID organizationId, String riotId);
}
