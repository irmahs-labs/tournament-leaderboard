package dev.irmahs.leaderboard.scoring;

import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.StatLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The only place a player's points are computed, from a season's own rules. For one game:
 *
 * <pre>
 * points = Σ points(stat) × stat          over the stats the season records and counts
 *        + kda points × (kills + assists) / max(deaths, 1)
 *        + Σ points of the bonuses won
 *        + points per win, when the player won
 * </pre>
 *
 * Over a match or a season, a player's points are the sum of their games' points. Points are
 * never stored, so changing a season's rules changes its leaderboard at once and leaves every other
 * season alone. A stat the season no longer records counts for nothing, even in games that have a
 * value for it.
 */
@Service
public class ScoringService {

    /** Points are kept to two decimals; screens show one. */
    public static final int SCALE = 2;

    /** A player's points for one game. A stat the game didn't record adds nothing. */
    public BigDecimal gamePoints(Season season, StatLine line, Collection<SeasonBonus> bonusesWon, boolean win) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Stat, StatRule> rule : season.getStatRules().entrySet()) {
            BigDecimal value = line.value(rule.getKey());
            if (value != null && rule.getKey().isCountable() && rule.getValue().counts()) {
                total = total.add(rule.getValue().points().multiply(value));
            }
        }
        BigDecimal kda = kda(season, line);
        if (kda != null) {
            total = total.add(season.getKdaPoints().multiply(kda));
        }
        for (SeasonBonus bonus : bonusesWon) {
            total = total.add(BigDecimal.valueOf(bonus.getPoints()));
        }
        if (win) {
            total = total.add(BigDecimal.valueOf(season.getPointsPerWin()));
        }
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The game's KDA ratio, (kills + assists) / max(deaths, 1), when it counts: the season gives it
     * points and records kills and deaths, and the line has both. Assists not recorded count as 0.
     */
    private static BigDecimal kda(Season season, StatLine line) {
        if (season.getKdaPoints().signum() == 0
                || !recorded(season, Stat.KILLS)
                || !recorded(season, Stat.DEATHS)
                || line.kills() == null
                || line.deaths() == null) {
            return null;
        }
        int assists = line.assists() == null || !recorded(season, Stat.ASSISTS) ? 0 : line.assists();
        return BigDecimal.valueOf(line.kills() + (long) assists)
                .divide(BigDecimal.valueOf(Math.max(line.deaths(), 1)), 6, RoundingMode.HALF_UP);
    }

    private static boolean recorded(Season season, Stat stat) {
        StatRule rule = season.getStatRules().get(stat);
        return rule != null && rule.recorded();
    }
}
