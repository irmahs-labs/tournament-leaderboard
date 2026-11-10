package dev.irmahs.leaderboard.seasons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.irmahs.leaderboard.seasons.Season.GameRange;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SeasonTest {

    private final Season season = new Season(UUID.randomUUID(), 1, "Season 1", 8, new GameRange(1, 5), 50);

    @ParameterizedTest
    @EnumSource(
            value = Stat.class,
            names = {"ADR", "DAMAGE_DELTA", "KAST_PCT", "HEADSHOT_PCT"})
    void anAverageOrAPercentageCanBeRecordedButNotCounted(Stat stat) {
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
            names = {"ADR", "DAMAGE_DELTA", "KAST_PCT", "HEADSHOT_PCT"})
    void everyOtherStatCanAddOrSubtract(Stat stat) {
        season.setStatRule(stat, new StatRule(true, -1));

        assertThat(season.getStatRules()).containsEntry(stat, new StatRule(true, -1));
    }
}
