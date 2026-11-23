package dev.irmahs.leaderboard.organizations;

import java.time.Instant;

/**
 * What every page of an organisation needs first: its names, language, accent colour, social
 * links and logo.
 *
 * @param accentColor #RRGGBB, or nothing to keep the template's red
 * @param logo the logo's address, which changes with the logo; nothing shows the initials
 */
public record OrganizationView(
        String slug,
        String name,
        String tournamentName,
        String description,
        String defaultLocale,
        String accentColor,
        int playersPerMatch,
        SocialLinks socialLinks,
        String logo) {

    static OrganizationView of(Organization organization, Instant logoUpdatedAt) {
        SocialLinks links = organization.getSocialLinks();
        return new OrganizationView(
                organization.getSlug(),
                organization.getName(),
                organization.getTournamentName(),
                organization.getDescription(),
                organization.getDefaultLocale(),
                organization.getAccentColor(),
                organization.getPlayersPerMatch(),
                links == null ? SocialLinks.NONE : links,
                logoUpdatedAt == null
                        ? null
                        : "/api/orgs/" + organization.getSlug() + "/logo?v=" + logoUpdatedAt.toEpochMilli());
    }
}
