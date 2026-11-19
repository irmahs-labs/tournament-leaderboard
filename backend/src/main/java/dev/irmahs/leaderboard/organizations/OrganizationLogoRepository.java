package dev.irmahs.leaderboard.organizations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrganizationLogoRepository extends JpaRepository<OrganizationLogo, UUID> {

    /** When the logo last changed, without reading the image: it makes the logo's address. */
    @Query("select l.updatedAt from OrganizationLogo l where l.organizationId = :organizationId")
    Optional<Instant> findUpdatedAt(UUID organizationId);
}
