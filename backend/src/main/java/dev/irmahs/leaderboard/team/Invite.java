package dev.irmahs.leaderboard.team;

import dev.irmahs.leaderboard.members.MemberRole;
import jakarta.persistence.Column;
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

/**
 * A code that adds someone to an organisation. Only a hash of the code is kept, so the database
 * can't give a working code back. A personal invite makes one Google account an admin, once.
 */
@Entity
@Table(name = "organization_invites")
public class Invite {

    /** Who can use a code: one Google account (personal), or anyone who has it (shared). */
    public enum Kind {
        PERSONAL,
        SHARED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    /** For a personal invite, the only Google email that can use it. */
    @Column(updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MemberRole role;

    @Column(nullable = false, unique = true, updatable = false)
    private byte[] codeHash;

    @Column(updatable = false)
    private UUID invitedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    /** How many times it can be used; empty means no limit. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer maxUses;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int useCount;

    private Instant revokedAt;

    protected Invite() {}

    private Invite(UUID organizationId, String email, byte[] codeHash, UUID invitedBy, Instant expiresAt) {
        this.organizationId = organizationId;
        this.kind = Kind.PERSONAL;
        this.email = email;
        this.role = MemberRole.ADMIN;
        this.codeHash = codeHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.maxUses = 1;
    }

    /** An invite for one Google account to become an admin, usable once. */
    static Invite personal(UUID organizationId, String email, byte[] codeHash, UUID invitedBy, Instant expiresAt) {
        return new Invite(organizationId, email, codeHash, invitedBy, expiresAt);
    }

    /** What the invite is at this moment: usable, or why not. */
    public InviteState state(Instant now) {
        if (revokedAt != null) {
            return InviteState.REVOKED;
        }
        if (maxUses != null && useCount >= maxUses) {
            return InviteState.USED;
        }
        return now.isBefore(expiresAt) ? InviteState.VALID : InviteState.EXPIRED;
    }

    void use() {
        useCount++;
    }

    void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getEmail() {
        return email;
    }

    public MemberRole getRole() {
        return role;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getUseCount() {
        return useCount;
    }
}
