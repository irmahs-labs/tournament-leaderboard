package dev.irmahs.leaderboard.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Someone who has signed in with Google. Created at their first sign-in. */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Google's stable id for the account. The email can change; this can't. */
    @Column(nullable = false, unique = true, updatable = false)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    private String displayName;

    /** The Riot ID every linked player profile shows; nothing until one is linked or it's set. */
    private String riotId;

    private String discordUsername;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {}

    /** The account of someone signing in for the first time. */
    AppUser(GoogleProfile profile, boolean platformAdmin) {
        this.googleSub = profile.sub();
        update(profile, platformAdmin);
    }

    /** Takes the latest details from Google, which the person may have changed there. */
    void update(GoogleProfile profile, boolean platformAdmin) {
        this.email = profile.email();
        this.displayName = profile.name();
        this.platformAdmin = platformAdmin;
    }

    /** The Riot ID and Discord username that follow the person to every organisation. */
    void identify(String riotId, String discordUsername) {
        this.riotId = riotId;
        this.discordUsername = discordUsername;
    }

    public String getRiotId() {
        return riotId;
    }

    public String getDiscordUsername() {
        return discordUsername;
    }

    public UUID getId() {
        return id;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
