package dev.irmahs.leaderboard.seasons;

/**
 * A bonus as the public pages show it.
 *
 * @param stat the stat compared; nothing for a manual bonus
 */
public record BonusView(String code, String label, BonusAward award, Stat stat, int points) {

    public static BonusView of(SeasonBonus bonus) {
        return new BonusView(bonus.getCode(), bonus.getLabel(), bonus.getAward(), bonus.getStat(), bonus.getPoints());
    }
}
