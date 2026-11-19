package dev.irmahs.leaderboard.organizations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Finds the organisation an address names. Everything public starts here. */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizations;

    OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /** Notes that one of the organisation's games was just saved or cleared. */
    @Transactional
    public void recordActivity(UUID organizationId) {
        organizations.findById(organizationId).ifPresent(organization -> organization.recordActivity(Instant.now()));
    }

    /** Whether an organisation, active or not, already has this address. */
    public boolean slugTaken(String slug) {
        return organizations.existsBySlug(slug);
    }

    /** A new organisation; the slug and name have been checked, and the slug is free. */
    @Transactional
    public Organization found(String slug, String name, UUID createdBy) {
        return organizations.saveAndFlush(Organization.founded(slug, name, createdBy));
    }

    /** The active organisation with the latest game, for the home page; none before any game. */
    public Optional<Organization> mostRecentlyActive() {
        return organizations.findFirstByStatusAndLastActivityAtNotNullOrderByLastActivityAtDesc(
                OrganizationStatus.ACTIVE);
    }

    /** The organisation with this slug, unless there's none or it's suspended. */
    public Optional<Organization> findActive(String slug) {
        return organizations.findBySlugAndStatus(slug, OrganizationStatus.ACTIVE);
    }

    /** The organisation with this id, unless there's none or it's suspended. */
    public Optional<Organization> findActive(UUID id) {
        return organizations.findById(id).filter(organization -> organization.getStatus() == OrganizationStatus.ACTIVE);
    }

    /**
     * The organisation with this slug. A suspended organisation is treated as if it didn't exist.
     *
     * @throws ResponseStatusException 404 when there's no such active organisation
     */
    public Organization requireActive(String slug) {
        return findActive(slug)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no organisation called " + slug));
    }
}
