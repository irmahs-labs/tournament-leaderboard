package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.matches.Game;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One player's line in one game: their stats, whether they won, and the bonuses they got. */
@Entity
@Table(name = "game_stats")
public class GameStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    private Integer performanceScore;

    private Integer acs;

    @Column(precision = 5, scale = 1)
    private BigDecimal adr;

    @Column(precision = 5, scale = 1)
    private BigDecimal damageDelta;

    @Column(precision = 5, scale = 2)
    private BigDecimal kastPct;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer kills;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer deaths;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer assists;

    private Integer econRating;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer firstBloods;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer plants;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer defuses;

    @Column(precision = 5, scale = 2)
    private BigDecimal headshotPct;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer clutches;

    @Column(nullable = false)
    private boolean win;

    @ManyToMany
    @JoinTable(
            name = "game_stat_bonuses",
            joinColumns = @JoinColumn(name = "game_stat_id"),
            inverseJoinColumns = @JoinColumn(name = "bonus_id"))
    private Set<SeasonBonus> bonuses = new HashSet<>();

    protected GameStat() {}

    public GameStat(Game game, Player player, StatLine line, boolean win) {
        this.game = game;
        this.player = player;
        this.performanceScore = line.performanceScore();
        this.acs = line.acs();
        this.adr = line.adr();
        this.damageDelta = line.damageDelta();
        this.kastPct = line.kastPct();
        this.kills = line.kills();
        this.deaths = line.deaths();
        this.assists = line.assists();
        this.econRating = line.econRating();
        this.firstBloods = line.firstBloods();
        this.plants = line.plants();
        this.defuses = line.defuses();
        this.headshotPct = line.headshotPct();
        this.clutches = line.clutches();
        this.win = win;
    }

    public void award(SeasonBonus bonus) {
        bonuses.add(bonus);
    }

    public Integer getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public Player getPlayer() {
        return player;
    }

    public StatLine getLine() {
        return new StatLine(
                performanceScore,
                acs,
                adr,
                damageDelta,
                kastPct,
                kills,
                deaths,
                assists,
                econRating,
                firstBloods,
                plants,
                defuses,
                headshotPct,
                clutches);
    }

    public boolean isWin() {
        return win;
    }

    public Set<SeasonBonus> getBonuses() {
        return Collections.unmodifiableSet(bonuses);
    }
}
