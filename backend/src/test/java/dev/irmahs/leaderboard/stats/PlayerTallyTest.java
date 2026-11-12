package dev.irmahs.leaderboard.stats;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.players.Player;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerTallyTest {

    private final Player kestrel = new Player(UUID.randomUUID(), "Kestrel#EUW", null, null);
    private final PlayerTally tally = new PlayerTally(kestrel);

    private static StatLine line(Integer kills, Integer deaths, Integer assists) {
        return new StatLine(null, null, null, null, null, kills, deaths, assists, null, null, null, null, null, null);
    }

    @Test
    void aPlayerWithNoDeathsHasAKdaOfKillsPlusAssists() {
        tally.add(new GameStat(null, kestrel, line(7, 0, 3), true));

        assertThat(tally.kda()).isEqualByComparingTo("10");
    }

    @Test
    void thereIsNoKdaWhenNoneOfItsStatsWasRecorded() {
        tally.add(new GameStat(null, kestrel, line(null, null, null), false));

        assertThat(tally.kda()).isNull();
        assertThat(tally.totals()).isEmpty();
        assertThat(tally.averages()).isEmpty();
        assertThat(tally.games()).isEqualTo(1);
        assertThat(tally.wins()).isZero();
    }

    @Test
    void kdaStillWorksWhenOnlyKillsWereRecorded() {
        tally.add(new GameStat(null, kestrel, line(9, null, null), false));

        assertThat(tally.kda()).isEqualByComparingTo("9");
    }

    @Test
    void anAverageOnlyCountsTheGamesThatRecordedTheStat() {
        tally.add(new GameStat(null, kestrel, line(10, 4, null), false));
        tally.add(new GameStat(null, kestrel, line(5, 5, 6), true));
        tally.add(new GameStat(null, kestrel, line(null, 6, null), false));

        assertThat(tally.averages().get(dev.irmahs.leaderboard.seasons.Stat.KILLS))
                .isEqualByComparingTo("7.50");
        assertThat(tally.averages().get(dev.irmahs.leaderboard.seasons.Stat.DEATHS))
                .isEqualByComparingTo("5.00");
        assertThat(tally.averages().get(dev.irmahs.leaderboard.seasons.Stat.ASSISTS))
                .isEqualByComparingTo("6.00");
        assertThat(tally.kda()).isEqualByComparingTo(new BigDecimal("1.40"));
    }
}
