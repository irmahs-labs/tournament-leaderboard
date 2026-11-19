package dev.irmahs.leaderboard.organizations;

/**
 * How an organisation presents itself, from the admin Branding screen.
 *
 * @param tournamentName such as "Spike Tournament"; empty for none
 * @param description one line for the home page card; empty for none
 * @param defaultLocale "en" or "fr"
 * @param accentColor #RRGGBB; empty keeps the template's red
 * @param playersPerMatch the places of each new match, 2 to 40
 */
public record Branding(
        String name,
        String tournamentName,
        String description,
        String defaultLocale,
        String accentColor,
        int playersPerMatch,
        SocialLinks socialLinks) {}
