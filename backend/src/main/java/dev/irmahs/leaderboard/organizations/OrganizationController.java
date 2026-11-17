package dev.irmahs.leaderboard.organizations;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** What every page of an organisation needs first: its names, language and accent colour. */
@RestController
public class OrganizationController {

    private final OrganizationService organizations;

    OrganizationController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @GetMapping("/api/orgs/{slug}")
    public OrganizationView organization(@PathVariable String slug) {
        Organization organization = organizations.requireActive(slug);
        return new OrganizationView(
                organization.getSlug(),
                organization.getName(),
                organization.getTournamentName(),
                organization.getDescription(),
                organization.getDefaultLocale(),
                organization.getAccentColor(),
                organization.getPlayersPerMatch());
    }

    /**
     * @param accentColor #RRGGBB, or nothing to keep the template's red
     */
    public record OrganizationView(
            String slug,
            String name,
            String tournamentName,
            String description,
            String defaultLocale,
            String accentColor,
            int playersPerMatch) {}
}
