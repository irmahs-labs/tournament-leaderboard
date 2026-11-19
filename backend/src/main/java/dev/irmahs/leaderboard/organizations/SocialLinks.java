package dev.irmahs.leaderboard.organizations;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Where else to find an organisation, shown in the footer of its pages. Each one may be empty. */
@Embeddable
public record SocialLinks(
        @Column(name = "discord_url", length = 200) String discord,
        @Column(name = "instagram_url", length = 200) String instagram,
        @Column(name = "twitch_url", length = 200) String twitch,
        @Column(name = "youtube_url", length = 200) String youtube,
        @Column(name = "tiktok_url", length = 200) String tiktok,
        @Column(name = "website_url", length = 200) String website) {

    public static final SocialLinks NONE = new SocialLinks(null, null, null, null, null, null);
}
