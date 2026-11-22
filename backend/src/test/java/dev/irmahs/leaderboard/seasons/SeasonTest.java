package dev.irmahs.leaderboard.seasons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import dev.irmahs.leaderboard.seasons.Season.GameRange;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SeasonTest {

    private final Season season = new Season(UUID.randomUUID(), 1, "Season 1", 8, new GameRange(1, 5), 50);

    @ParameterizedTest
    @EnumSource(
            value = Stat.class,
            names = {"ADR", "DAMAGE_DELTA", "KAST_PCT", "HEADSHOT_PCT", "PLACE"})
    void anAverageAPercentageOrThePlaceCanBeRecordedButNotCounted(Stat stat) {
        season.setStatRule(stat, new StatRule(true, 0));

        assertThat(season.getStatRules()).containsEntry(stat, new StatRule(true, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> season.setStatRule(stat, new StatRule(true, 1)))
                .withMessageContaining(stat.name());
    }

    @ParameterizedTest
    @EnumSource(
            value = Stat.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {"ADR", "DAMAGE_DELTA", "KAST_PCT", "HEADSHOT_PCT", "PLACE"})
    void everyOtherStatCanAddOrSubtract(Stat stat) {
        season.setStatRule(stat, new StatRule(true, -1));

        assertThat(season.getStatRules()).containsEntry(stat, new StatRule(true, -1));
    }

    @Test
    void thePlatformDefaultsKeepTheSeasonsOwnBonusesAndRedefineTheirs() {
        season.addBonus("FB", "First bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 25);
        season.addBonus("ACE", "Ace", BonusAward.MANUAL, null, 15);

        season.usePlatformDefaults();

        assertThat(season.getStatRules()).hasSize(Stat.values().length);
        assertThat(season.getStatRules().get(Stat.ASSISTS)).isEqualTo(new StatRule(true, 1.5));
        assertThat(season.getKdaPoints()).isEqualByComparingTo("10");
        assertThat(season.getBonuses())
                .extracting(SeasonBonus::getCode, SeasonBonus::getPoints)
                .containsExactly(
                        tuple("FB", 10),
                        tuple("ACE", 15),
                        tuple("LD", 10),
                        tuple("MA", 10),
                        tuple("MP", 10),
                        tuple("MD", 10),
                        tuple("MVP", 20),
                        tuple("SVP", 10),
                        tuple("HON", 10));
    }

    @Test
    void aCopyTakesTheRulesAndTheBonusesInOrder() {
        season.setStatRule(Stat.KILLS, new StatRule(true, 1));
        season.addBonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10);
        season.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);
        Season next = new Season(season.getOrganizationId(), 2, "Season 2", 8, new GameRange(1, 5), 50);

        next.copyRulesFrom(season);

        assertThat(next.getStatRules()).isEqualTo(season.getStatRules());
        assertThat(next.getBonuses())
                .extracting(SeasonBonus::getCode, SeasonBonus::getPosition, SeasonBonus::getPoints)
                .containsExactly(tuple("FB", 1, 10), tuple("MVP", 2, 25));
        assertThat(next.getBonuses())
                .allSatisfy(bonus -> assertThat(bonus.getSeason()).isSameAs(next));
    }

    @Test
    void finishingTwiceKeepsTheFirstTime() {
        Instant first = Instant.parse("2026-11-01T20:00:00Z");
        season.finish(first);
        season.finish(first.plusSeconds(60));

        assertThat(season.getFinishedAt()).isEqualTo(first);
        season.reopen();
        assertThat(season.getFinishedAt()).isNull();
    }
}
