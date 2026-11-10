package dev.irmahs.leaderboard.matches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One game of a match. */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false)
    private Match match;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int gameNumber;

    protected Game() {}

    public Game(Match match, int gameNumber) {
        this.match = match;
        this.gameNumber = gameNumber;
    }

    public Integer getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public int getGameNumber() {
        return gameNumber;
    }
}
