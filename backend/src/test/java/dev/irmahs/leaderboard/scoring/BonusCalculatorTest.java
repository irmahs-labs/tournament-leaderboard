package dev.irmahs.leaderboard.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.scoring.BonusCalculator.Entry;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.StatLine;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BonusCalculatorTest {

    private final BonusCalculator calculator = new BonusCalculator();
    private final Season season = TodaysRules.season();

    private Map<String, Set<String>> award(Season rules, Map<String, StatLine> lines) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        lines.forEach((player, line) -> entries.put(player, new Entry(line, false)));
        return codes(rules, entries);
    }

    private Map<String, Set<String>> codes(Season rules, Map<String, Entry> entries) {
        Map<String, Set<String>> codes = new LinkedHashMap<>();
        calculator
                .award(rules, entries)
                .forEach((player, bonuses) -> codes.put(
                        player,
                        Set.copyOf(bonuses.stream().map(SeasonBonus::getCode).toList())));
        return codes;
    }

    @Test
    void givesEachBonusToTheBestPlayer() {
        Map<String, Set<String>> awarded = award(
                season,
                Map.of(
                        "kestrel", TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 0),
                        "wren", TodaysRules.line(180, 12, 9, 8, 40, 1, 0, 1),
                        "heron", TodaysRules.line(120, 7, 15, 2, 30, 0, 1, 0)));

        assertThat(awarded)
                .containsEntry("kestrel", Set.of("FB", "MP"))
                .containsEntry("wren", Set.of("LD", "MA", "MD"))
                .containsEntry("heron", Set.of());
    }

    @Test
    void playersTiedForTheBestValueAllWin() {
        Map<String, Set<String>> awarded = award(
                season,
                Map.of(
                        "kestrel", TodaysRules.line(240, 18, 9, 8, 60, 3, 0, 0),
                        "wren", TodaysRules.line(180, 12, 9, 8, 40, 3, 0, 0),
                        "heron", TodaysRules.line(120, 7, 15, 2, 30, 0, 0, 0)));

        assertThat(awarded)
                .containsEntry("kestrel", Set.of("FB", "LD", "MA"))
                .containsEntry("wren", Set.of("FB", "LD", "MA"))
                .containsEntry("heron", Set.of());
    }

    @Test
    void whenEveryoneHasZeroOnlyTheLowestBonusIsGiven() {
        Map<String, Set<String>> awarded = award(
                season,
                Map.of(
                        "kestrel", TodaysRules.line(0, 0, 0, 0, 0, 0, 0, 0),
                        "wren", TodaysRules.line(0, 0, 0, 0, 0, 0, 0, 0)));

        assertThat(awarded).containsEntry("kestrel", Set.of("LD")).containsEntry("wren", Set.of("LD"));
    }

    @Test
    void worksWithASinglePlayer() {
        Map<String, Set<String>> awarded =
                award(season, Map.of("kestrel", TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 1)));

        assertThat(awarded).containsEntry("kestrel", Set.of("FB", "LD", "MA", "MP", "MD"));
    }

    @Test
    void givesNothingWhenThereAreNoPlayers() {
        assertThat(calculator.award(season, Map.of())).isEmpty();
    }

    @Test
    void aPlayerWhoseStatIsEmptyCannotWinIt() {
        StatLine noDeathsRecorded =
                new StatLine(200, null, null, null, null, 10, null, 4, 50, 1, 0, 0, null, null, null, null);
        Map<String, Set<String>> awarded = award(
                season, Map.of("kestrel", noDeathsRecorded, "wren", TodaysRules.line(180, 12, 9, 3, 40, 0, 0, 0)));

        assertThat(awarded).containsEntry("kestrel", Set.of("FB", "MA")).containsEntry("wren", Set.of("LD"));
    }

    @Test
    void aBonusNobodyHasAValueForIsNotGiven() {
        Map<String, Set<String>> awarded =
                award(season, Map.of("kestrel", TodaysRules.emptyLine(), "wren", TodaysRules.emptyLine()));

        assertThat(awarded).containsEntry("kestrel", Set.of()).containsEntry("wren", Set.of());
    }

    @Test
    void manualBonusesAreLeftToTheAdmin() {
        Season cup = new Season(season.getOrganizationId(), 2, "Winter Cup", 4, new Season.GameRange(3, 3), 30);
        cup.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);

        assertThat(award(cup, Map.of("kestrel", TodaysRules.line(240, 18, 12, 5, 60, 3, 2, 1))))
                .containsEntry("kestrel", Set.of());
    }

    @Test
    void aBonusCanCompareAnAverage() {
        Season cup = new Season(season.getOrganizationId(), 2, "Winter Cup", 4, new Season.GameRange(3, 3), 30);
        cup.setStatRule(Stat.ADR, new StatRule(true, 0));
        cup.setStatRule(Stat.DAMAGE_DELTA, new StatRule(true, 0));
        cup.addBonus("DMG", "Highest damage per round", BonusAward.HIGHEST, Stat.ADR, 15);
        cup.addBonus("DD", "Best damage delta", BonusAward.HIGHEST, Stat.DAMAGE_DELTA, 15);

        Map<String, Set<String>> awarded =
                award(cup, Map.of("kestrel", averages("152.3", "-4.0"), "wren", averages("152.30", "-12.5")));

        // Nobody's damage delta is above 0, so that bonus goes to nobody.
        assertThat(awarded).containsEntry("kestrel", Set.of("DMG")).containsEntry("wren", Set.of("DMG"));
    }

    @Test
    void theHighestPercentageWinsItsBonus() {
        Season cup = new Season(season.getOrganizationId(), 2, "Winter Cup", 4, new Season.GameRange(3, 3), 30);
        cup.setStatRule(Stat.KAST_PCT, new StatRule(true, 0));
        cup.setStatRule(Stat.HEADSHOT_PCT, new StatRule(true, 0));
        cup.addBonus("KAST", "Highest KAST %", BonusAward.HIGHEST, Stat.KAST_PCT, 10);
        cup.addBonus("HS", "Highest headshot %", BonusAward.HIGHEST, Stat.HEADSHOT_PCT, 10);

        Map<String, Set<String>> awarded = award(
                cup,
                Map.of(
                        "kestrel", percentages("74.00", "21.50"),
                        "wren", percentages("68.25", "33.00"),
                        "heron", percentages("74.00", "12.00")));

        assertThat(awarded)
                .containsEntry("kestrel", Set.of("KAST"))
                .containsEntry("wren", Set.of("HS"))
                .containsEntry("heron", Set.of("KAST"));
    }

    @Test
    void mvpAndSvpGoToTheBestPlaceOnEachTeam() {
        Season rules = placedSeason();

        Map<String, Set<String>> awarded = codes(
                rules,
                Map.of(
                        "kestrel", new Entry(placed(1, 0), false),
                        "wren", new Entry(placed(2, 0), true),
                        "heron", new Entry(placed(3, 0), true),
                        "finch", new Entry(placed(4, 0), false)));

        assertThat(awarded)
                .containsEntry("kestrel", Set.of("SVP"))
                .containsEntry("wren", Set.of("MVP"))
                .containsEntry("heron", Set.of())
                .containsEntry("finch", Set.of());
    }

    @Test
    void nobodyIsMvpWhenNobodyWonOrNobodyHasAPlace() {
        Season rules = placedSeason();

        assertThat(codes(rules, Map.of("kestrel", new Entry(placed(1, 0), false))))
                .containsEntry("kestrel", Set.of("SVP"));
        assertThat(codes(rules, Map.of("kestrel", new Entry(TodaysRules.emptyLine(), true))))
                .containsEntry("kestrel", Set.of());
    }

    @Test
    void theHonourBonusGoesToEveryoneAtTheThreshold() {
        Season rules = placedSeason();
        rules.setStatRule(Stat.HONOURS, new StatRule(true, 5));

        Map<String, Set<String>> awarded = codes(
                rules,
                Map.of(
                        "kestrel", new Entry(placed(5, 3), false),
                        "wren", new Entry(placed(6, 4), false),
                        "heron", new Entry(placed(7, 2), false)));

        // All three lost, so the best place, Kestrel's, is also SVP.
        assertThat(awarded)
                .containsEntry("kestrel", Set.of("SVP", "HON"))
                .containsEntry("wren", Set.of("HON"))
                .containsEntry("heron", Set.of());
    }

    @Test
    void noBonusComesFromAStatTheSeasonDoesNotRecord() {
        Season rules = placedSeason();
        rules.setStatRule(Stat.PLACE, new StatRule(false, 0));

        assertThat(codes(rules, Map.of("kestrel", new Entry(placed(1, 5), true))))
                .containsEntry("kestrel", Set.of());
    }

    /** A season recording the place and honours (switched off), with MVP, SVP and HON bonuses. */
    private Season placedSeason() {
        Season rules = new Season(season.getOrganizationId(), 9, "Season 9", 8, new Season.GameRange(1, 5), 50);
        rules.setStatRule(Stat.PLACE, new StatRule(true, 0));
        rules.setStatRule(Stat.HONOURS, new StatRule(false, 5));
        rules.addBonus("MVP", "Best on the winning team", BonusAward.TOP_WINNER, Stat.PLACE, 20);
        rules.addBonus("SVP", "Best on the losing team", BonusAward.TOP_LOSER, Stat.PLACE, 10);
        rules.addBonus("HON", "Most honourable", BonusAward.AT_LEAST, Stat.HONOURS, 10, BigDecimal.valueOf(3));
        return rules;
    }

    private static StatLine placed(int place, int honours) {
        return new StatLine(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, place, honours);
    }

    private static StatLine percentages(String kastPct, String headshotPct) {
        return new StatLine(
                null,
                null,
                null,
                null,
                new BigDecimal(kastPct),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal(headshotPct),
                null,
                null,
                null);
    }

    private static StatLine averages(String adr, String damageDelta) {
        return new StatLine(
                null,
                null,
                new BigDecimal(adr),
                new BigDecimal(damageDelta),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void bonusesKeepTheSeasonsOrder() {
        assertThat(season.getBonuses())
                .extracting(SeasonBonus::getCode)
                .isEqualTo(List.of("FB", "LD", "MA", "MP", "MD"));
    }
}
