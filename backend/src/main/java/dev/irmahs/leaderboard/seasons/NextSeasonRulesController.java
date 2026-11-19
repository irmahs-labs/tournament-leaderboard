package dev.irmahs.leaderboard.seasons;

import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.seasons.SeasonService.RulesView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The rules saved for the next season: shown, and offered when a season starts. Admins only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin/next-season-rules")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class NextSeasonRulesController {

    private final OrganizationService organizations;
    private final NextSeasonRules nextSeason;

    NextSeasonRulesController(OrganizationService organizations, NextSeasonRules nextSeason) {
        this.organizations = organizations;
        this.nextSeason = nextSeason;
    }

    /** The saved rules; 204 when none are. */
    @GetMapping
    public ResponseEntity<RulesView> rules(@PathVariable String slug) {
        UUID organizationId = organizations.requireActive(slug).getId();
        return nextSeason
                .load(organizationId)
                .map(SeasonService::rulesOf)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable String slug) {
        nextSeason.clear(organizations.requireActive(slug).getId());
    }
}
