package dev.irmahs.leaderboard.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.StatLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

    /** The points of one game, for rules whose points are whole. */
    private long points(Season season, StatLine line, List<SeasonBonus> won, boolean win) {
        return scoring.gamePoints(season, line, won, win).longValueExact();
    }

    /**
     * The live site's formula, copied from legacy/src/constants/scoring.ts. Its "performanceScore" is the
     * performance score here.
     */
    private static long legacyScore(
            int performanceScore,
            int kills,
            int deaths,
            int assists,
            int econRating,
            int firstBloods,
            int plants,
            int defuses,
            int bonusCount,
            int wins) {
        return performanceScore
                + kills
                + assists
                + econRating
                + firstBloods
                + plants
                + defuses
                + bonusCount * 10L
                - deaths
                + wins * 50L;
    }

    @ParameterizedTest
    @CsvSource({
        "240, 18, 12, 5, 60, 3, 2, 1, 1, true",
        "0, 0, 0, 0, 0, 0, 0, 0, 0, false",
        "95, 4, 21, 2, 18, 0, 0, 0, 0, false",
        "412, 31, 9, 11, 97, 7, 4, 3, 5, true",
    })
    void todaysRulesGiveTheLiveSitesGamePoints(
            int performanceScore,
            int kills,
            int deaths,
            int assists,
            int econRating,
            int firstBloods,
            int plants,
            int defuses,
            int bonusCount,
            boolean win) {
        Season season = TodaysRules.season();
        StatLine line =
                TodaysRules.line(performanceScore, kills, deaths, assists, econRating, firstBloods, plants, defuses);
        List<SeasonBonus> won = season.getBonuses().subList(0, bonusCount);

        assertThat(points(season, line, won, win))
                .isEqualTo(legacyScore(
                        performanceScore,
                        kills,
                        deaths,
                        assists,
                        econRating,
                        firstBloods,
                        plants,
                        defuses,
                        bonusCount,
                        win ? 1 : 0));
    }

    @Test
    void aStatTheGameDidNotRecordAddsNothing() {
        Season season = TodaysRules.season();

        assertThat(points(season, TodaysRules.emptyLine(), List.of(), false)).isZero();
    }

    @Test
    void aStatWithoutARuleIsNotCounted() {
        Season season = TodaysRules.season();
        StatLine line =
                new StatLine(null, 300, null, null, null, 10, null, null, null, null, null, null, null, 4, null, null);

        assertThat(points(season, line, List.of(), false)).isEqualTo(10);
    }

    @Test
    void aStatTheSeasonStopsRecordingStopsCountingInItsOldGamesToo() {
        Season season = TodaysRules.season();
        season.setStatRule(Stat.PERFORMANCE_SCORE, new StatRule(false, 1));
        StatLine oldGame = TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 1);

        assertThat(points(season, oldGame, List.of(), false))
                .isEqualTo(legacyScore(240, 18, 12, 5, 60, 3, 2, 1, 0, 0) - 240);
    }

    @Test
    void eachStatCountsItsOwnPointsAndTheKdaRatioAddsItsOwn() {
        Season season = new Season(UUID.randomUUID(), 9, "Season 9", 8, new Season.GameRange(1, 5), 50);
        season.setStatRule(Stat.KILLS, new StatRule(true, 1));
        season.setStatRule(Stat.DEATHS, new StatRule(true, -1));
        season.setStatRule(Stat.ASSISTS, new StatRule(true, 1.5));
        season.setStatRule(Stat.FIRST_BLOODS, new StatRule(true, 4));
        season.setStatRule(Stat.PLANTS, new StatRule(true, 2));
        season.setStatRule(Stat.DEFUSES, new StatRule(true, 2));
        season.setPoints(50, BigDecimal.TEN);
        // 24/10/3 with 2 first bloods: 24 - 10 + 4.5 + 8, and a KDA of 27 / 10 = 2.7, worth 27.
        StatLine line = new StatLine(null, null, null, null, null, 24, 10, 3, null, 2, 0, 0, null, null, null, null);

        assertThat(scoring.gamePoints(season, line, List.of(), false)).isEqualByComparingTo("53.5");
        assertThat(scoring.gamePoints(season, line, List.of(), true)).isEqualByComparingTo("103.5");
    }

    @Test
    void theKdaRatioCountsNoDeathsAsOne() {
        Season season = new Season(UUID.randomUUID(), 9, "Season 9", 8, new Season.GameRange(1, 5), 50);
        season.setStatRule(Stat.KILLS, new StatRule(true, 0));
        season.setStatRule(Stat.DEATHS, new StatRule(true, 0));
        season.setStatRule(Stat.ASSISTS, new StatRule(true, 0));
        season.setPoints(50, BigDecimal.TEN);
        StatLine flawless =
                new StatLine(null, null, null, null, null, 7, 0, 2, null, null, null, null, null, null, null, null);
        StatLine third =
                new StatLine(null, null, null, null, null, 1, 3, 0, null, null, null, null, null, null, null, null);

        assertThat(scoring.gamePoints(season, flawless, List.of(), false)).isEqualByComparingTo("90");
        assertThat(scoring.gamePoints(season, third, List.of(), false)).isEqualByComparingTo("3.33");
    }

    @Test
    void honoursCountOnlyWhileTheSeasonRecordsThem() {
        Season season = new Season(UUID.randomUUID(), 9, "Season 9", 8, new Season.GameRange(1, 5), 50);
        season.setStatRule(Stat.HONOURS, new StatRule(false, 5));
        StatLine honoured =
                new StatLine(null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 3);

        assertThat(scoring.gamePoints(season, honoured, List.of(), false)).isZero();
        season.setStatRule(Stat.HONOURS, new StatRule(true, 5));
        assertThat(scoring.gamePoints(season, honoured, List.of(), false)).isEqualByComparingTo("15");
    }

    @Test
    void eachSeasonCountsWithItsOwnRules() {
        Season lyon = TodaysRules.season();
        lyon.setStatRule(Stat.DEATHS, new StatRule(true, 0));
        Season cup = new Season(lyon.getOrganizationId(), 2, "Winter Cup", 4, new Season.GameRange(3, 3), 30);
        cup.setStatRule(Stat.KILLS, new StatRule(true, 1));
        SeasonBonus mvp = cup.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);
        StatLine line = TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 1);

        assertThat(points(lyon, line, List.of(), true)).isEqualTo(240 + 18 + 5 + 60 + 3 + 2 + 1 + 50);
        assertThat(points(cup, line, List.of(mvp), true)).isEqualTo(18 + 25 + 30);
    }

    @Test
    void averagesAndPercentagesNeverCount() {
        Season season = TodaysRules.season();
        StatLine line = new StatLine(
                null,
                null,
                new BigDecimal("152.3"),
                new BigDecimal("-12.5"),
                new BigDecimal("74.00"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("27.50"),
                null,
                null,
                null);

        assertThat(points(season, line, List.of(), false)).isZero();
    }
}
