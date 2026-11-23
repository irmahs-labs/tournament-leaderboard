package dev.irmahs.leaderboard.seasons;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A season's rules on their own: each stat's rule, the points for a win and for KDA, and the
 * bonuses. Taken from one season and given to another, or kept for the next season.
 */
public record RulesSnapshot(
        int pointsPerWin, BigDecimal kdaPoints, Map<Stat, StatRule> stats, List<BonusView> bonuses) {

    public static RulesSnapshot of(Season season) {
        return new RulesSnapshot(
                season.getPointsPerWin(),
                season.getKdaPoints(),
                new EnumMap<>(season.getStatRules()),
                season.getBonuses().stream().map(BonusView::of).toList());
    }
}
