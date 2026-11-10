package dev.irmahs.leaderboard.scoring;

import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.StatLine;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The only place a player's points are computed, from a season's own rules:
 *
 * <pre>total = Σ effect(stat) × stat + Σ points of the bonuses won + wins × points per win</pre>
 *
 * Totals are never stored, so changing a season's rules changes its leaderboard at once and
 * leaves every other season alone.
 */
@Service
public class ScoringService {

    /**
     * A player's points over any number of games of one season.
     *
     * @param statSums the player's stats added up over those games; a missing stat counts as 0
     * @param bonusPoints the points of every bonus the player won in those games
     * @param wins how many of those games the player won
     */
    public long points(Season season, Map<Stat, Long> statSums, long bonusPoints, long wins) {
        long total = bonusPoints + wins * season.getPointsPerWin();
        for (Map.Entry<Stat, StatRule> rule : season.getStatRules().entrySet()) {
            total += rule.getValue().effect() * statSums.getOrDefault(rule.getKey(), 0L);
        }
        return total;
    }

    /** A player's points for one game. A stat the game didn't record adds nothing. */
    public long gamePoints(Season season, StatLine line, Collection<SeasonBonus> bonusesWon, boolean win) {
        Map<Stat, Long> stats = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            BigDecimal value = line.value(stat);
            if (stat.isCountable() && value != null) {
                stats.put(stat, value.longValueExact());
            }
        }
        long bonusPoints = bonusesWon.stream().mapToLong(SeasonBonus::getPoints).sum();
        return points(season, stats, bonusPoints, win ? 1 : 0);
    }
}
