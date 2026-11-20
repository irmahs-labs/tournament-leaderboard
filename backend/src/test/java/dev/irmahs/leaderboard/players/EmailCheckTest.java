package dev.irmahs.leaderboard.players;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailCheckTest {

    @ParameterizedTest
    @ValueSource(strings = {"swift@example.com", "a.b@mail.example.fr", "x@y.z"})
    void anEmailLooksLikeOne(String email) {
        assertThat(PlayerAdminService.looksLikeEmail(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not an email",
                "@example.com",
                "a@@example.com",
                "a@b@c.com",
                "a@example",
                "a@.com",
                "a@example.",
                "a b@example.com"
            })
    void anythingElseDoesNot(String email) {
        assertThat(PlayerAdminService.looksLikeEmail(email)).isFalse();
    }
}
