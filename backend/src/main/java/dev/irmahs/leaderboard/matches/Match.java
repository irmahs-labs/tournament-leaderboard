package dev.irmahs.leaderboard.matches;

import dev.irmahs.leaderboard.seasons.Season;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One evening of a season, made of games. Its number restarts at 1 in each season. */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false, updatable = false)
    private Season season;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int matchNumber;

    /** A date in the future makes it the "Next match". */
    private LocalDate matchDate;

    private LocalTime startTime;

    /** A venue address or a Discord channel. */
    @Column(length = 200)
    private String location;

    /** The day players are asked to answer by; answers still count after it, until the lock. */
    private LocalDate replyBy;

    /** How many players it takes; "I'm in" after that joins the waiting list. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int places = 10;

    /** Whether the first player waiting takes a place as soon as one frees up. */
    @Column(nullable = false)
    private boolean autoPromote = true;

    /** Set once the admins lock the roster: nobody's answer changes any more. */
    private Instant lockedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.PLANNED;

    protected Match() {}

    public Match(Season season, int matchNumber, LocalDate matchDate) {
        this.organizationId = season.getOrganizationId();
        this.season = season;
        this.matchNumber = matchNumber;
        this.matchDate = matchDate;
    }

    /** Moves the match to another day, or leaves it undated. */
    public void reschedule(LocalDate matchDate) {
        this.matchDate = matchDate;
    }

    /** Plans when and where it's played, and how many players it takes. */
    public void plan(Plan plan) {
        this.matchDate = plan.date();
        this.startTime = plan.startTime();
        this.location = plan.location();
        this.replyBy = plan.replyBy();
        this.places = plan.places();
    }

    /** Turns automatic moves up the waiting list on, or off so the admins decide by hand. */
    public void setAutoPromote(boolean autoPromote) {
        this.autoPromote = autoPromote;
    }

    public boolean isAutoPromote() {
        return autoPromote;
    }

    public void cancel() {
        status = MatchStatus.CANCELLED;
    }

    public void restore() {
        status = MatchStatus.PLANNED;
    }

    /** Freezes the roster, for example on match day. Locking it again keeps the first time. */
    public void lock(Instant at) {
        if (lockedAt == null) {
            lockedAt = at;
        }
    }

    public void unlock() {
        lockedAt = null;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    public boolean isCancelled() {
        return status == MatchStatus.CANCELLED;
    }

    /**
     * When and where a match is played, and how many players it takes.
     *
     * @param location a venue address or a Discord channel
     */
    public record Plan(LocalDate date, LocalTime startTime, String location, LocalDate replyBy, int places) {}

    public Integer getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Season getSeason() {
        return season;
    }

    public int getMatchNumber() {
        return matchNumber;
    }

    public LocalDate getMatchDate() {
        return matchDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public String getLocation() {
        return location;
    }

    public LocalDate getReplyBy() {
        return replyBy;
    }

    public int getPlaces() {
        return places;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public MatchStatus getStatus() {
        return status;
    }
}
