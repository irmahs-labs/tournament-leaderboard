package dev.irmahs.leaderboard.scoring;

import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.StatLine;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * Works out who wins a game's automatic bonuses:
 *
 * <ul>
 *   <li>a "highest" bonus goes to the highest value of its stat, but to nobody when that value
 *       is 0 or less;
 *   <li>a "lowest" bonus goes to the lowest value, even 0;
 *   <li>a "top winner" bonus (MVP) goes to the best place, the lowest value of its stat, among the
 *       players who won the game; a "top loser" bonus (SVP) to the best place among those who lost;
 *   <li>an "at least" bonus goes to every player whose stat reaches its threshold;
 *   <li>players tied for the best value all win it;
 *   <li>a player whose stat is empty can't win it, and nobody wins a bonus whose stat the season
 *       doesn't record.
 * </ul>
 *
 * Manual bonuses are never worked out: an admin ticks them.
 */
@Service
public class BonusCalculator {

    /** One player's line in the game, and whether they won it. */
    public record Entry(StatLine line, boolean win) {}

    /**
     * @param entries each player's line in the game, by player
     * @param <P> whatever identifies a player to the caller
     * @return for each player, the automatic bonuses they win; empty for a player who wins none
     */
    public <P> Map<P, Set<SeasonBonus>> award(Season season, Map<P, Entry> entries) {
        Map<P, Set<SeasonBonus>> awarded = new HashMap<>();
        entries.keySet().forEach(player -> awarded.put(player, new HashSet<>()));

        for (SeasonBonus bonus : season.getBonuses()) {
            if (bonus.getAward() == BonusAward.MANUAL || !recorded(season, bonus.getStat())) {
                continue;
            }
            Map<P, BigDecimal> values = new HashMap<>();
            entries.forEach((player, entry) -> {
                BigDecimal value = entry.line().value(bonus.getStat());
                if (value != null && competes(bonus.getAward(), entry.win())) {
                    values.put(player, value);
                }
            });
            winning(bonus, values.values())
                    .ifPresent(winning -> values.forEach((player, value) -> {
                        if (winning.test(value)) {
                            awarded.get(player).add(bonus);
                        }
                    }));
        }
        return awarded;
    }

    private static boolean recorded(Season season, Stat stat) {
        StatRule rule = season.getStatRules().get(stat);
        return rule != null && rule.recorded();
    }

    /** Whether a player takes part in the bonus: MVP is among the winners, SVP among the losers. */
    private static boolean competes(BonusAward award, boolean win) {
        return switch (award) {
            case TOP_WINNER -> win;
            case TOP_LOSER -> !win;
            default -> true;
        };
    }

    /** Which values win the bonus, or nothing when nobody can win it. */
    private static Optional<Predicate<BigDecimal>> winning(SeasonBonus bonus, Collection<BigDecimal> values) {
        return switch (bonus.getAward()) {
            case AT_LEAST -> Optional.of(value -> value.compareTo(bonus.getThreshold()) >= 0);
            case HIGHEST ->
                values.stream()
                        .max(Comparator.naturalOrder())
                        .filter(best -> best.signum() > 0)
                        .map(BonusCalculator::equalTo);
            case LOWEST, TOP_WINNER, TOP_LOSER ->
                values.stream().min(Comparator.naturalOrder()).map(BonusCalculator::equalTo);
            case MANUAL -> Optional.empty();
        };
    }

    private static Predicate<BigDecimal> equalTo(BigDecimal best) {
        return value -> value.compareTo(best) == 0;
    }
}
