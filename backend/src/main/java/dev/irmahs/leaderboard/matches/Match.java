package dev.irmahs.leaderboard.matches;

import dev.irmahs.leaderboard.seasons.Season;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    protected Match() {}

    public Match(Season season, int matchNumber, LocalDate matchDate) {
        this.organizationId = season.getOrganizationId();
        this.season = season;
        this.matchNumber = matchNumber;
        this.matchDate = matchDate;
    }

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
}
