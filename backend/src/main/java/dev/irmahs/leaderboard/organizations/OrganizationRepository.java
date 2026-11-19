package dev.irmahs.leaderboard.organizations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlugAndStatus(String slug, OrganizationStatus status);

    boolean existsBySlug(String slug);

    Optional<Organization> findBySlug(String slug);

    List<Organization> findAllByOrderByCreatedAtAsc();

    /** The active organisation that saved or cleared a game most recently. */
    Optional<Organization> findFirstByStatusAndLastActivityAtNotNullOrderByLastActivityAtDesc(
            OrganizationStatus status);
}
