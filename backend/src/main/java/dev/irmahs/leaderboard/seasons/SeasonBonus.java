package dev.irmahs.leaderboard.seasons;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A bonus a season defines: how it's won and what it's worth. */
@Entity
@Table(name = "season_bonuses")
public class SeasonBonus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false, updatable = false)
    private Season season;

    /** The short tag in the score table: FB, LD, or an admin's own. Unique within the season. */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BonusAward award;

    /** The stat compared; empty for a {@link BonusAward#MANUAL} bonus. */
    @Enumerated(EnumType.STRING)
    private Stat stat;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int points;

    /** Its order on screen. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int position;

    protected SeasonBonus() {}

    SeasonBonus(Season season, String code, String label, BonusAward award, Stat stat, int points, int position) {
        this.season = season;
        this.code = code;
        this.label = label;
        this.award = award;
        this.stat = stat;
        this.points = points;
        this.position = position;
    }

    public Integer getId() {
        return id;
    }

    public Season getSeason() {
        return season;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public BonusAward getAward() {
        return award;
    }

    public Stat getStat() {
        return stat;
    }

    public int getPoints() {
        return points;
    }

    public int getPosition() {
        return position;
    }
}
