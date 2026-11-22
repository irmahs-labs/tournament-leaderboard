package dev.irmahs.leaderboard.players;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class RiotIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"Kestrel#EUW", "Abe#ONE", " Huzéguto#1234 ", "Jett Main 2026#FR1", "NS Papyyy67#EUW"})
    void acceptsANameAndATagAsRiotMakesThem(String riotId) {
        assertThat(RiotIds.isRiotId(riotId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Drrako", "Ab#EUW", "Kestrel#EU", "Kestrel#EUROPE", "Kestrel#EU-W", "A#B#CDE", "#EUW", ""})
    void refusesAnythingElse(String riotId) {
        assertThat(RiotIds.isRiotId(riotId)).isFalse();
    }

    @Test
    void requiresOneAndTrimsIt() {
        assertThat(RiotIds.require(" Kestrel#EUW ")).isEqualTo("Kestrel#EUW");
        assertThatThrownBy(() -> RiotIds.require("Drrako"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("such as Kestrel#EUW");
    }
}
