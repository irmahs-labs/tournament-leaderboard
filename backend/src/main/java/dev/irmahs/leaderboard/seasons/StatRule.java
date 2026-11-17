package dev.irmahs.leaderboard.seasons;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * How a season treats one stat.
 *
 * @param recorded whether admins are asked for it in games entered from now on
 * @param effect what its value does to a player's points: 1 adds, -1 subtracts, 0 isn't counted
 */
@Embeddable
public record StatRule(
        @Column(nullable = false) boolean recorded,

        @JdbcTypeCode(SqlTypes.SMALLINT) @Column(nullable = false)
        int effect) {}
