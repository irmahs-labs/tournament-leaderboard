package dev.irmahs.leaderboard.ranks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A Valorant rank, such as Gold 2. Shared by every organisation. */
@Entity
@Table(name = "ranks")
public class Rank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer id;

    @Column(nullable = false)
    private String rankName;

    /** Orders the ranks from lowest to highest. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer rankValue;

    /** The rank's colour on the Players screen, as #RRGGBB. */
    @Column(length = 7)
    private String color;

    protected Rank() {}

    public Rank(String rankName, Integer rankValue, String color) {
        this.rankName = rankName;
        this.rankValue = rankValue;
        this.color = color;
    }

    public Integer getId() {
        return id;
    }

    public String getRankName() {
        return rankName;
    }

    public Integer getRankValue() {
        return rankValue;
    }

    public String getColor() {
        return color;
    }
}
