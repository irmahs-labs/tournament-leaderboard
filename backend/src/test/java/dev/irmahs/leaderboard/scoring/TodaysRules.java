package dev.irmahs.leaderboard.scoring;

import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.StatLine;
import java.util.List;
import java.util.UUID;

/**
 * The live site's fixed formula, written as a season's rules, plus short ways to build lines. What
 * the live site calls "performanceScore" is Valorant's performance score here; ACS is the tracker's own number.
 */
final class TodaysRules {

    static final List<Stat> ADDING = List.of(
            Stat.PERFORMANCE_SCORE,
            Stat.KILLS,
            Stat.ASSISTS,
            Stat.ECON_RATING,
            Stat.FIRST_BLOODS,
            Stat.PLANTS,
            Stat.DEFUSES);

    private TodaysRules() {}

    /** Every stat adds except deaths, which subtract; five bonuses at 10; 50 per win. */
    static Season season() {
        Season season = new Season(UUID.randomUUID(), 1, "Season 1", 8, new GameRange(1, 5), 50);
        ADDING.forEach(stat -> season.setStatRule(stat, new StatRule(true, 1)));
        season.setStatRule(Stat.DEATHS, new StatRule(true, -1));
        season.addBonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10);
        season.addBonus("LD", "Fewest deaths", BonusAward.LOWEST, Stat.DEATHS, 10);
        season.addBonus("MA", "Most assists", BonusAward.HIGHEST, Stat.ASSISTS, 10);
        season.addBonus("MP", "Most plants", BonusAward.HIGHEST, Stat.PLANTS, 10);
        season.addBonus("MD", "Most defuses", BonusAward.HIGHEST, Stat.DEFUSES, 10);
        return season;
    }

    /** A line with the eight stats the live site records, in its column order. */
    static StatLine line(
            int performanceScore,
            int kills,
            int deaths,
            int assists,
            int econRating,
            int firstBloods,
            int plants,
            int defuses) {
        return new StatLine(
                performanceScore,
                null,
                null,
                null,
                null,
                kills,
                deaths,
                assists,
                econRating,
                firstBloods,
                plants,
                defuses,
                null,
                null);
    }

    static StatLine emptyLine() {
        return new StatLine(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
