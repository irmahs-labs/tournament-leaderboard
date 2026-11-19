package dev.irmahs.leaderboard.organizations;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The sites an organisation can link to, and the addresses each accepts: https only, on that
 * site or one of its subdomains; the website can be anywhere.
 */
enum SocialSite {
    DISCORD("discord.gg", "discord.com"),
    INSTAGRAM("instagram.com"),
    TWITCH("twitch.tv"),
    YOUTUBE("youtube.com", "youtu.be"),
    TIKTOK("tiktok.com"),
    WEBSITE;

    static final int MAX_LENGTH = 200;

    private final List<String> hosts;

    SocialSite(String... hosts) {
        this.hosts = List.of(hosts);
    }

    /**
     * The address tidied up, nothing when it's empty.
     *
     * @throws IllegalArgumentException when it isn't an https address on this site
     */
    Optional<String> check(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        String trimmed = address.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("is longer than " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("isn't a web address");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isEmpty() || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("must be an https:// address");
        }
        if (!hosts.isEmpty() && hosts.stream().noneMatch(site -> host.equals(site) || host.endsWith("." + site))) {
            throw new IllegalArgumentException("must be on " + String.join(" or ", hosts));
        }
        return Optional.of(trimmed);
    }
}
