package dev.irmahs.leaderboard.claims;

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

/** A member asking to be linked to a player record; an admin accepts or declines it. */
@Entity
@Table(name = "player_claims")
class PlayerClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, updatable = false)
    private UUID playerId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(length = 200)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.PENDING;

    @Column(length = 200)
    private String reason;

    @Column(nullable = false)
    private boolean selfApproved;

    private UUID decidedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant decidedAt;

    protected PlayerClaim() {}

    PlayerClaim(UUID organizationId, UUID playerId, UUID userId, String note) {
        this.organizationId = organizationId;
        this.playerId = playerId;
        this.userId = userId;
        this.note = note;
    }

    void accept(UUID admin, boolean ownClaim) {
        decide(ClaimStatus.ACCEPTED, admin, null);
        this.selfApproved = ownClaim;
    }

    void decline(UUID admin, String why) {
        decide(ClaimStatus.DECLINED, admin, why);
    }

    void cancel() {
        decide(ClaimStatus.CANCELLED, null, null);
    }

    private void decide(ClaimStatus newStatus, UUID admin, String why) {
        this.status = newStatus;
        this.decidedBy = admin;
        this.reason = why;
        this.decidedAt = Instant.now();
    }

    UUID getId() {
        return id;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    UUID getPlayerId() {
        return playerId;
    }

    UUID getUserId() {
        return userId;
    }

    String getNote() {
        return note;
    }

    ClaimStatus getStatus() {
        return status;
    }

    String getReason() {
        return reason;
    }

    boolean isSelfApproved() {
        return selfApproved;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }
}
