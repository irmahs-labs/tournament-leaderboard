package dev.irmahs.leaderboard.claims;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PlayerClaimRepository extends JpaRepository<PlayerClaim, UUID> {

    Optional<PlayerClaim> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<PlayerClaim> findByOrganizationIdAndStatusOrderByCreatedAt(UUID organizationId, ClaimStatus status);

    List<PlayerClaim> findByOrganizationIdAndPlayerIdAndStatus(UUID organizationId, UUID playerId, ClaimStatus status);

    /** The member's latest claim that wasn't cancelled: the one waiting, or the last answered. */
    Optional<PlayerClaim> findFirstByOrganizationIdAndUserIdAndStatusNotOrderByCreatedAtDesc(
            UUID organizationId, UUID userId, ClaimStatus status);

    /** How many games each of the organisation's players has, as (player id, games) pairs. */
    @Query("""
            select new dev.irmahs.leaderboard.claims.GameCount(line.player.id, count(line))
            from GameStat line where line.player.organizationId = :organizationId
            group by line.player.id""")
    List<GameCount> countGames(UUID organizationId);
}
