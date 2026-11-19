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
import java.math.BigDecimal;
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

    /** What each game's KDA ratio is worth: (kills + assists) / max(deaths, 1) times this. */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal kdaPoints = BigDecimal.ZERO;

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

    /**
     * @throws IllegalArgumentException when the rule counts a stat that can't count, such as a
     *     percentage
     */
    public void setStatRule(Stat stat, StatRule rule) {
        if (rule.points().signum() != 0 && !stat.isCountable()) {
            throw new IllegalArgumentException(stat + " can be shown but can't count towards points");
        }
        statRules.put(stat, rule);
    }

    /** Renames the season and changes its shape: dates, number of matches and games per match. */
    public void edit(String name, LocalDate startsOn, LocalDate endsOn, int matchCount, GameRange games) {
        this.name = name;
        setDates(startsOn, endsOn);
        this.matchCount = matchCount;
        this.minGames = games.min();
        this.maxGames = games.max();
    }

    /** Closes the season. Closing it again keeps the first time. */
    public void finish(Instant at) {
        if (finishedAt == null) {
            finishedAt = at;
        }
    }

    public void reopen() {
        finishedAt = null;
    }

    /** Sets what a win and each game's KDA ratio are worth. */
    public void setPoints(int pointsPerWin, BigDecimal kdaPoints) {
        this.pointsPerWin = pointsPerWin;
        this.kdaPoints = kdaPoints;
    }

    /**
     * Gives the season the platform's rules: every stat rule, the points for a win and for KDA, and
     * the platform's bonuses. A bonus it already has under the same code takes the platform's way of
     * being won and its points; its other bonuses stay.
     */
    public void usePlatformDefaults() {
        PlatformDefaults.apply(this);
    }

    /** Takes another season's stat rules and bonuses, for a new season with no games yet. */
    public void copyRulesFrom(Season other) {
        statRules.clear();
        statRules.putAll(other.statRules);
        pointsPerWin = other.pointsPerWin;
        kdaPoints = other.kdaPoints;
        bonuses.clear();
        other.bonuses.forEach(bonus -> addBonus(
                bonus.getCode(),
                bonus.getLabel(),
                bonus.getAward(),
                bonus.getStat(),
                bonus.getPoints(),
                bonus.getThreshold()));
    }

    /** Adds a bonus at the end of the season's list. */
    public SeasonBonus addBonus(String code, String label, BonusAward award, Stat stat, int points) {
        return addBonus(code, label, award, stat, points, null);
    }

    /**
     * Adds a bonus at the end of the season's list.
     *
     * @param threshold for an {@link BonusAward#AT_LEAST} bonus, the value to reach; nothing otherwise
     */
    public SeasonBonus addBonus(
            String code, String label, BonusAward award, Stat stat, int points, BigDecimal threshold) {
        SeasonBonus bonus = new SeasonBonus(this, code, label, award, stat, points, bonuses.size() + 1);
        bonus.change(points, threshold);
        bonuses.add(bonus);
        return bonus;
    }

    /** Removes a bonus; the ones after it move up. Its awards go with it. */
    public void removeBonus(SeasonBonus bonus) {
        bonuses.remove(bonus);
        for (int i = 0; i < bonuses.size(); i++) {
            bonuses.get(i).moveTo(i + 1);
        }
    }

    /** The bonus with this code, or nothing. */
    public java.util.Optional<SeasonBonus> bonus(String code) {
        return bonuses.stream()
                .filter(bonus -> bonus.getCode().equalsIgnoreCase(code))
                .findFirst();
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

    public BigDecimal getKdaPoints() {
        return kdaPoints;
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
