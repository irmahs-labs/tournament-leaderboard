package dev.irmahs.leaderboard.seasons;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The rules a new season starts with when it copies no other, and that "Use the platform defaults"
 * applies. Riot's custom games no longer show the performance score or economy, so neither is
 * recorded; points come from the stats the scoreboard still shows, each game's KDA ratio, the
 * scoreboard place (through MVP and SVP), the bonuses and wins. Honours are ready but switched off.
 */
final class PlatformDefaults {

    static final int MATCH_COUNT = 8;
    static final Season.GameRange GAMES = new Season.GameRange(1, 5);
    static final int POINTS_PER_WIN = 50;
    static final BigDecimal KDA_POINTS = BigDecimal.TEN;

    private static final Map<Stat, StatRule> RULES = Map.ofEntries(
            Map.entry(Stat.PERFORMANCE_SCORE, new StatRule(false, 0)),
            Map.entry(Stat.ACS, new StatRule(false, 0)),
            Map.entry(Stat.ADR, new StatRule(false, 0)),
            Map.entry(Stat.DAMAGE_DELTA, new StatRule(false, 0)),
            Map.entry(Stat.KAST_PCT, new StatRule(false, 0)),
            Map.entry(Stat.KILLS, new StatRule(true, 1)),
            Map.entry(Stat.DEATHS, new StatRule(true, -1)),
            Map.entry(Stat.ASSISTS, new StatRule(true, 1.5)),
            Map.entry(Stat.ECON_RATING, new StatRule(false, 0)),
            Map.entry(Stat.FIRST_BLOODS, new StatRule(true, 4)),
            Map.entry(Stat.PLANTS, new StatRule(true, 2)),
            Map.entry(Stat.DEFUSES, new StatRule(true, 2)),
            Map.entry(Stat.HEADSHOT_PCT, new StatRule(false, 0)),
            Map.entry(Stat.CLUTCHES, new StatRule(false, 0)),
            Map.entry(Stat.PLACE, new StatRule(true, 0)),
            Map.entry(Stat.HONOURS, new StatRule(false, 5)));

    /** The bonuses, in the order they're shown. */
    private static final List<Bonus> BONUSES = List.of(
            new Bonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10, null),
            new Bonus("LD", "Fewest deaths", BonusAward.LOWEST, Stat.DEATHS, 10, null),
            new Bonus("MA", "Most assists", BonusAward.HIGHEST, Stat.ASSISTS, 10, null),
            new Bonus("MP", "Most plants", BonusAward.HIGHEST, Stat.PLANTS, 10, null),
            new Bonus("MD", "Most defuses", BonusAward.HIGHEST, Stat.DEFUSES, 10, null),
            new Bonus("MVP", "Best on the winning team", BonusAward.TOP_WINNER, Stat.PLACE, 20, null),
            new Bonus("SVP", "Best on the losing team", BonusAward.TOP_LOSER, Stat.PLACE, 10, null),
            new Bonus("HON", "Most honourable", BonusAward.AT_LEAST, Stat.HONOURS, 10, BigDecimal.valueOf(3)));

    private record Bonus(String code, String label, BonusAward award, Stat stat, int points, BigDecimal threshold) {}

    private PlatformDefaults() {}

    /**
     * Gives a season the platform's rules: every stat rule, the points for a win and for KDA, and
     * the platform's bonuses. A bonus the season already has under the same code takes the
     * platform's way of being won and its points; the season's other bonuses stay as they are.
     */
    static void apply(Season season) {
        for (Stat stat : Stat.values()) {
            season.setStatRule(stat, RULES.get(stat));
        }
        season.setPoints(POINTS_PER_WIN, KDA_POINTS);
        for (Bonus bonus : BONUSES) {
            season.bonus(bonus.code())
                    .ifPresentOrElse(
                            existing -> existing.redefine(
                                    bonus.label(), bonus.award(), bonus.stat(), bonus.points(), bonus.threshold()),
                            () -> season.addBonus(
                                    bonus.code(),
                                    bonus.label(),
                                    bonus.award(),
                                    bonus.stat(),
                                    bonus.points(),
                                    bonus.threshold()));
        }
    }
}
