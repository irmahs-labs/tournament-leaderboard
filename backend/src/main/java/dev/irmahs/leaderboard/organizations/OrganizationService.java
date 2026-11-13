package dev.irmahs.leaderboard.organizations;

import java.util.Optional;
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

    /** The organisation with this slug, unless there's none or it's suspended. */
    public Optional<Organization> findActive(String slug) {
        return organizations.findBySlugAndStatus(slug, OrganizationStatus.ACTIVE);
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
