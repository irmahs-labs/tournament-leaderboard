package dev.irmahs.leaderboard.seasons;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A season of one organisation, with its own scoring rules. Its number restarts at 1 in each
 * organisation, and changing its rules never touches another season's results.
 */
@Entity
@Table(name = "seasons")
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String name;

    private LocalDate startsOn;

    private LocalDate endsOn;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int matchCount;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int minGames;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int maxGames;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int pointsPerWin;

    private Instant finishedAt;

    @ElementCollection
    @CollectionTable(name = "season_stat_rules", joinColumns = @JoinColumn(name = "season_id"))
    @MapKeyColumn(name = "stat")
    @MapKeyEnumerated(EnumType.STRING)
    private Map<Stat, StatRule> statRules = new EnumMap<>(Stat.class);

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<SeasonBonus> bonuses = new ArrayList<>();

    protected Season() {}

    /**
     * @param games the fewest and the most games a match can have
     */
    public Season(UUID organizationId, int number, String name, int matchCount, GameRange games, int pointsPerWin) {
        this.organizationId = organizationId;
        this.number = number;
        this.name = name;
        this.matchCount = matchCount;
        this.minGames = games.min();
        this.maxGames = games.max();
        this.pointsPerWin = pointsPerWin;
    }

    /** The fewest and the most games a match of the season can have. */
    public record GameRange(int min, int max) {}

    public void setDates(LocalDate startsOn, LocalDate endsOn) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public void setStatRule(Stat stat, StatRule rule) {
        statRules.put(stat, rule);
    }

    /** Adds a bonus at the end of the season's list. */
    public SeasonBonus addBonus(String code, String label, BonusAward award, Stat stat, int points) {
        SeasonBonus bonus = new SeasonBonus(this, code, label, award, stat, points, bonuses.size() + 1);
        bonuses.add(bonus);
        return bonus;
    }

    public Integer getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public int getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public GameRange getGames() {
        return new GameRange(minGames, maxGames);
    }

    public int getPointsPerWin() {
        return pointsPerWin;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Map<Stat, StatRule> getStatRules() {
        return Collections.unmodifiableMap(statRules);
    }

    public List<SeasonBonus> getBonuses() {
        return Collections.unmodifiableList(bonuses);
    }
}
