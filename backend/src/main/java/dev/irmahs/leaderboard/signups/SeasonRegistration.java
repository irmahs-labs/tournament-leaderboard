package dev.irmahs.leaderboard.signups;

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
 * A player signed up for a season. Withdrawing or being taken off keeps the row, so signing up
 * again starts a new place in the order.
 */
@Entity
@Table(name = "season_registrations")
@IdClass(SeasonRegistration.Key.class)
class SeasonRegistration {

    @Id
    @Column(nullable = false, updatable = false)
    private Integer seasonId;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID playerId;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(nullable = false)
    private Instant registeredAt = Instant.now();

    /** The admin who signed them up; nothing when the player did. */
    private UUID addedBy;

    protected SeasonRegistration() {}

    SeasonRegistration(Integer seasonId, UUID playerId, UUID organizationId, UUID addedBy) {
        this.seasonId = seasonId;
        this.playerId = playerId;
        this.organizationId = organizationId;
        this.addedBy = addedBy;
    }

    /** Signs them up again, from now: by themselves when no admin is given. */
    void register(UUID admin) {
        status = RegistrationStatus.REGISTERED;
        registeredAt = Instant.now();
        addedBy = admin;
    }

    void withdraw() {
        status = RegistrationStatus.WITHDRAWN;
    }

    void remove() {
        status = RegistrationStatus.REMOVED;
    }

    boolean isRegistered() {
        return status == RegistrationStatus.REGISTERED;
    }

    Integer getSeasonId() {
        return seasonId;
    }

    UUID getPlayerId() {
        return playerId;
    }

    RegistrationStatus getStatus() {
        return status;
    }

    Instant getRegisteredAt() {
        return registeredAt;
    }

    UUID getAddedBy() {
        return addedBy;
    }

    record Key(Integer seasonId, UUID playerId) implements Serializable {}
}
