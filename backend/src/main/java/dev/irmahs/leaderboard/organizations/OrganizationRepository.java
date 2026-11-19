package dev.irmahs.leaderboard.organizations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlugAndStatus(String slug, OrganizationStatus status);

    boolean existsBySlug(String slug);

    /** The active organisation that saved or cleared a game most recently. */
    Optional<Organization> findFirstByStatusAndLastActivityAtNotNullOrderByLastActivityAtDesc(
            OrganizationStatus status);
}
