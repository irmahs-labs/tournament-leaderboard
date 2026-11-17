package dev.irmahs.leaderboard.members;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * An account's membership of one organisation. An account has at most one per organisation, an
 * organisation has one owner at most, and a player is linked to one membership at most.
 */
@Entity
@Table(name = "organization_members")
@IdClass(OrganizationMember.Key.class)
public class OrganizationMember {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Column(name = "is_owner", nullable = false)
    private boolean owner;

    /** The player this member is on the leaderboard, once their profile is linked. */
    private UUID playerId;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected OrganizationMember() {}

    private OrganizationMember(UUID userId, UUID organizationId, MemberRole role, boolean owner) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.role = role;
        this.owner = owner;
    }

    public static OrganizationMember admin(UUID userId, UUID organizationId) {
        return new OrganizationMember(userId, organizationId, MemberRole.ADMIN, false);
    }

    /** The organisation's owner, who is also an admin. */
    public static OrganizationMember owner(UUID userId, UUID organizationId) {
        return new OrganizationMember(userId, organizationId, MemberRole.ADMIN, true);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public MemberRole getRole() {
        return role;
    }

    public boolean isOwner() {
        return owner;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** The primary key: one membership per account and organisation. */
    public record Key(UUID userId, UUID organizationId) implements Serializable {}
}
