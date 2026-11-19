package dev.irmahs.leaderboard.organizations;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A venue that runs its own tournament: its seasons, players and matches all belong to it. */
@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The name in the address: /api/orgs/{slug}. */
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String tournamentName;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, length = 2)
    private String defaultLocale = "en";

    /** Replaces the template's red, as #RRGGBB; empty keeps the red. */
    @Column(length = 7)
    private String accentColor;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int playersPerMatch = 10;

    @Embedded
    private SocialLinks socialLinks;

    /** Set when a game is saved or deleted; the home page features the most recent one. */
    private Instant lastActivityAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationStatus status = OrganizationStatus.ACTIVE;

    /** Who created it; empty for the organisations that came with the import. */
    private UUID createdBy;

    /** Set by the database. */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    protected Organization() {}

    public Organization(String slug, String name, String tournamentName) {
        this.slug = slug;
        this.name = name;
        this.tournamentName = tournamentName;
    }

    /** A new organisation, created by a signed-in person who becomes its owner. */
    static Organization founded(String slug, String name, UUID createdBy) {
        Organization organization = new Organization(slug, name, null);
        organization.createdBy = createdBy;
        return organization;
    }

    /** Notes that a game was just saved or cleared; the home page features the most recent. */
    void recordActivity(Instant when) {
        this.lastActivityAt = when;
    }

    /** Changes how the organisation presents itself; the values have been checked already. */
    void rebrand(Branding branding) {
        this.name = branding.name();
        this.tournamentName = branding.tournamentName();
        this.description = branding.description();
        this.defaultLocale = branding.defaultLocale();
        this.accentColor = branding.accentColor();
        this.playersPerMatch = branding.playersPerMatch();
        this.socialLinks = branding.socialLinks();
    }

    /** Suspended, its pages and admin area answer Not found; nothing is deleted. */
    void changeStatus(OrganizationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getTournamentName() {
        return tournamentName;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public int getPlayersPerMatch() {
        return playersPerMatch;
    }

    /** Nothing when none is set. */
    public SocialLinks getSocialLinks() {
        return socialLinks;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public OrganizationStatus getStatus() {
        return status;
    }
}
