package dev.irmahs.leaderboard.seasons;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a season treats one stat.
 *
 * @param recorded whether admins are asked for it; a stat that isn't recorded doesn't count either,
 *     in any game of the season, even those that have a value for it
 * @param points what each unit of it is worth, negative to subtract; 0 doesn't count it
 */
@Embeddable
public record StatRule(
        @Column(nullable = false) boolean recorded,

        @Column(nullable = false, precision = 6, scale = 2) BigDecimal points) {

    /** Points are kept to two decimals, as the database keeps them, so equal rules are equal. */
    public StatRule {
        points = points.setScale(2, RoundingMode.HALF_UP);
    }

    public StatRule(boolean recorded, double points) {
        this(recorded, BigDecimal.valueOf(points));
    }

    /** Whether the stat adds to or subtracts from the points: recorded, and worth something. */
    public boolean counts() {
        return recorded && points.signum() != 0;
    }
}
