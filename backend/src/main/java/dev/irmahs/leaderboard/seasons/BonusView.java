package dev.irmahs.leaderboard.seasons;

import java.math.BigDecimal;

/**
 * A bonus as the public pages show it.
 *
 * @param stat the stat compared; nothing for a manual bonus
 * @param threshold the value to reach, for an {@link BonusAward#AT_LEAST} bonus; nothing otherwise
 */
public record BonusView(String code, String label, BonusAward award, Stat stat, int points, BigDecimal threshold) {

    public static BonusView of(SeasonBonus bonus) {
        return new BonusView(
                bonus.getCode(),
                bonus.getLabel(),
                bonus.getAward(),
                bonus.getStat(),
                bonus.getPoints(),
                Points.plain(bonus.getThreshold()));
    }
}
