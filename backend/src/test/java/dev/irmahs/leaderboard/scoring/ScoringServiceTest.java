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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringServiceTest {

    private final ScoringService scoring = new ScoringService();

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

        assertThat(scoring.gamePoints(season, line, won, win))
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
    void todaysRulesGiveTheLiveSitesTotalOverSeveralGames() {
        Map<Stat, Long> sums = Map.of(
                Stat.PERFORMANCE_SCORE, 1890L,
                Stat.KILLS, 132L,
                Stat.DEATHS, 101L,
                Stat.ASSISTS, 44L,
                Stat.ECON_RATING, 480L,
                Stat.FIRST_BLOODS, 19L,
                Stat.PLANTS, 12L,
                Stat.DEFUSES, 6L);

        assertThat(scoring.points(TodaysRules.season(), sums, 7 * 10L, 5))
                .isEqualTo(legacyScore(1890, 132, 101, 44, 480, 19, 12, 6, 7, 5));
    }

    @Test
    void aStatTheGameDidNotRecordAddsNothing() {
        Season season = TodaysRules.season();

        assertThat(scoring.gamePoints(season, TodaysRules.emptyLine(), List.of(), false))
                .isZero();
        assertThat(scoring.points(season, Map.of(), 0, 0)).isZero();
    }

    @Test
    void aStatWithoutARuleIsNotCounted() {
        Season season = TodaysRules.season();
        StatLine line = new StatLine(null, 300, null, null, null, 10, null, null, null, null, null, null, null, 4);

        assertThat(scoring.gamePoints(season, line, List.of(), false)).isEqualTo(10);
    }

    @Test
    void aSeasonThatStopsRecordingAStatKeepsCountingItsOldGames() {
        Season season = TodaysRules.season();
        season.setStatRule(Stat.PERFORMANCE_SCORE, new StatRule(false, 1));
        StatLine oldGame = TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 1);

        assertThat(scoring.gamePoints(season, oldGame, List.of(), false))
                .isEqualTo(legacyScore(240, 18, 12, 5, 60, 3, 2, 1, 0, 0));
    }

    @Test
    void eachSeasonCountsWithItsOwnRules() {
        Season lyon = TodaysRules.season();
        lyon.setStatRule(Stat.DEATHS, new StatRule(true, 0));
        Season cup = new Season(lyon.getOrganizationId(), 2, "Winter Cup", 4, new Season.GameRange(3, 3), 30);
        cup.setStatRule(Stat.KILLS, new StatRule(true, 1));
        SeasonBonus mvp = cup.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);
        StatLine line = TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 1);

        assertThat(scoring.gamePoints(lyon, line, List.of(), true)).isEqualTo(240 + 18 + 5 + 60 + 3 + 2 + 1 + 50);
        assertThat(scoring.gamePoints(cup, line, List.of(mvp), true)).isEqualTo(18 + 25 + 30);
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
                null);

        assertThat(scoring.gamePoints(season, line, List.of(), false)).isZero();
    }
}
