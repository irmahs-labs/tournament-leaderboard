package dev.irmahs.leaderboard.scoring;

import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.stats.StatLine;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Works out who wins a game's automatic bonuses, the way the live site always has:
 *
 * <ul>
 *   <li>a "highest" bonus goes to the highest value of its stat, but to nobody when that value
 *       is 0 or less;
 *   <li>a "lowest" bonus goes to the lowest value, even 0;
 *   <li>players tied for the best value all win it;
 *   <li>a player whose stat is empty can't win it.
 * </ul>
 *
 * Manual bonuses are never worked out: an admin ticks them.
 */
@Service
public class BonusCalculator {

    /**
     * @param lines each player's line in the game, by player
     * @param <P> whatever identifies a player to the caller
     * @return for each player, the automatic bonuses they win; empty for a player who wins none
     */
    public <P> Map<P, Set<SeasonBonus>> award(Season season, Map<P, StatLine> lines) {
        Map<P, Set<SeasonBonus>> awarded = new HashMap<>();
        lines.keySet().forEach(player -> awarded.put(player, new HashSet<>()));

        for (SeasonBonus bonus : season.getBonuses()) {
            if (bonus.getAward() == BonusAward.MANUAL) {
                continue;
            }
            best(bonus, lines)
                    .ifPresent(best -> lines.forEach((player, line) -> {
                        BigDecimal value = line.value(bonus.getStat());
                        if (value != null && value.compareTo(best) == 0) {
                            awarded.get(player).add(bonus);
                        }
                    }));
        }
        return awarded;
    }

    /** The value that wins the bonus, or nothing when nobody can win it. */
    private static <P> Optional<BigDecimal> best(SeasonBonus bonus, Map<P, StatLine> lines) {
        boolean highest = bonus.getAward() == BonusAward.HIGHEST;
        Comparator<BigDecimal> order = highest ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return lines.values().stream()
                .map(line -> line.value(bonus.getStat()))
                .filter(Objects::nonNull)
                .max(order)
                .filter(best -> !highest || best.signum() > 0);
    }
}
