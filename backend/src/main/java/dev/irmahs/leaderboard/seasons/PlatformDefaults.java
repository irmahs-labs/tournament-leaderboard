package dev.irmahs.leaderboard.seasons;

import java.util.Map;

/**
 * The rules a new season starts with when it copies no other: today's Alsace Arena rules, without
 * performance score and economy, which Riot's custom games no longer show.
 */
final class PlatformDefaults {

    static final int MATCH_COUNT = 8;
    static final Season.GameRange GAMES = new Season.GameRange(1, 5);
    static final int POINTS_PER_WIN = 50;

    private static final Map<Stat, StatRule> RULES = Map.ofEntries(
            Map.entry(Stat.PERFORMANCE_SCORE, new StatRule(false, 1)),
            Map.entry(Stat.ACS, new StatRule(false, 0)),
            Map.entry(Stat.ADR, new StatRule(false, 0)),
            Map.entry(Stat.DAMAGE_DELTA, new StatRule(false, 0)),
            Map.entry(Stat.KAST_PCT, new StatRule(false, 0)),
            Map.entry(Stat.KILLS, new StatRule(true, 1)),
            Map.entry(Stat.DEATHS, new StatRule(true, -1)),
            Map.entry(Stat.ASSISTS, new StatRule(true, 1)),
            Map.entry(Stat.ECON_RATING, new StatRule(false, 1)),
            Map.entry(Stat.FIRST_BLOODS, new StatRule(true, 1)),
            Map.entry(Stat.PLANTS, new StatRule(true, 1)),
            Map.entry(Stat.DEFUSES, new StatRule(true, 1)),
            Map.entry(Stat.HEADSHOT_PCT, new StatRule(false, 0)),
            Map.entry(Stat.CLUTCHES, new StatRule(false, 0)));

    private PlatformDefaults() {}

    static void apply(Season season) {
        for (Stat stat : Stat.values()) {
            season.setStatRule(stat, RULES.get(stat));
        }
        season.addBonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10);
        season.addBonus("LD", "Fewest deaths", BonusAward.LOWEST, Stat.DEATHS, 10);
        season.addBonus("MA", "Most assists", BonusAward.HIGHEST, Stat.ASSISTS, 10);
        season.addBonus("MP", "Most plants", BonusAward.HIGHEST, Stat.PLANTS, 10);
        season.addBonus("MD", "Most defuses", BonusAward.HIGHEST, Stat.DEFUSES, 10);
    }
}
