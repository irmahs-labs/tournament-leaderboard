package dev.irmahs.leaderboard.rosters;

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

/** One player's answer for one match. No row means they haven't answered. */
@Entity
@Table(name = "match_attendance")
@IdClass(Attendance.Key.class)
class Attendance {

    @Id
    @Column(nullable = false, updatable = false)
    private Integer matchId;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID playerId;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceSource source;

    /** When they joined the waiting list: its order. */
    private Instant queuedAt;

    @Column(nullable = false)
    private Instant answeredAt;

    /** Who answered: the player's account, or an admin's. */
    private UUID answeredBy;

    protected Attendance() {}

    Attendance(Integer matchId, UUID playerId, UUID organizationId) {
        this.matchId = matchId;
        this.playerId = playerId;
        this.organizationId = organizationId;
    }

    /** Records an answer. Joining the waiting list again keeps one's place in it. */
    void answer(AttendanceStatus status, AttendanceSource source, UUID by, Instant at) {
        if (status == AttendanceStatus.WAITING) {
            if (this.status != AttendanceStatus.WAITING || queuedAt == null) {
                queuedAt = at;
            }
        } else {
            queuedAt = null;
        }
        this.status = status;
        this.source = source;
        this.answeredBy = by;
        this.answeredAt = at;
    }

    Integer getMatchId() {
        return matchId;
    }

    UUID getPlayerId() {
        return playerId;
    }

    AttendanceStatus getStatus() {
        return status;
    }

    AttendanceSource getSource() {
        return source;
    }

    Instant getQueuedAt() {
        return queuedAt;
    }

    Instant getAnsweredAt() {
        return answeredAt;
    }

    UUID getAnsweredBy() {
        return answeredBy;
    }

    boolean is(AttendanceStatus wanted) {
        return status == wanted;
    }

    record Key(Integer matchId, UUID playerId) implements Serializable {}
}
