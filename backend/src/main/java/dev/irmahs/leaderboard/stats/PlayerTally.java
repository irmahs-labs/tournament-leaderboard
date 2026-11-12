package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/** One player's lines added up, over a match or a season. */
final class PlayerTally {

    private final Player player;
    private final Map<Stat, BigDecimal> sums = new EnumMap<>(Stat.class);
    private final Map<Stat, Integer> gamesRecording = new EnumMap<>(Stat.class);
    private final Map<String, Integer> bonusCounts = new TreeMap<>();
    private int games;
    private int wins;
    private long bonusPoints;

    PlayerTally(Player player) {
        this.player = player;
    }

    void add(GameStat line) {
        games++;
        if (line.isWin()) {
            wins++;
        }
        for (Stat stat : Stat.values()) {
            BigDecimal value = line.getLine().value(stat);
            if (value != null) {
                sums.merge(stat, value, BigDecimal::add);
                gamesRecording.merge(stat, 1, Integer::sum);
            }
        }
        for (SeasonBonus bonus : line.getBonuses()) {
            bonusCounts.merge(bonus.getCode(), 1, Integer::sum);
            bonusPoints += bonus.getPoints();
        }
    }

    Player player() {
        return player;
    }

    int games() {
        return games;
    }

    int wins() {
        return wins;
    }

    long bonusPoints() {
        return bonusPoints;
    }

    /** How many times the player won each bonus, by its code. */
    Map<String, Integer> bonusCounts() {
        return bonusCounts;
    }

    /** The countable stats added up. Averages and percentages are left out: their sum means nothing. */
    Map<Stat, Long> totals() {
        Map<Stat, Long> totals = new EnumMap<>(Stat.class);
        sums.forEach((stat, sum) -> {
            if (stat.isCountable()) {
                totals.put(stat, sum.longValueExact());
            }
        });
        return totals;
    }

    /** Each stat per game that recorded it, to two decimals. */
    Map<Stat, BigDecimal> averages() {
        Map<Stat, BigDecimal> averages = new EnumMap<>(Stat.class);
        sums.forEach((stat, sum) ->
                averages.put(stat, sum.divide(BigDecimal.valueOf(gamesRecording.get(stat)), 2, RoundingMode.HALF_UP)));
        return averages;
    }

    /** (kills + assists) / deaths, counting no deaths as one; nothing when none of the three was recorded. */
    BigDecimal kda() {
        if (!sums.containsKey(Stat.KILLS) && !sums.containsKey(Stat.ASSISTS) && !sums.containsKey(Stat.DEATHS)) {
            return null;
        }
        BigDecimal killsAndAssists = sum(Stat.KILLS).add(sum(Stat.ASSISTS));
        return killsAndAssists.divide(sum(Stat.DEATHS).max(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(Stat stat) {
        return sums.getOrDefault(stat, BigDecimal.ZERO);
    }
}
