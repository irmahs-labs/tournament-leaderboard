package dev.irmahs.leaderboard.team;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TeamServiceTest {

    @ParameterizedTest
    @CsvSource({
        "quentin@alsacearena.com, true",
        "a.b+c@mail.example.fr, true",
        "not an email, false",
        "@example.com, false",
        "quentin@, false",
        "quentin@example, false",
        "quentin@example., false",
        "a@b@example.com, false",
        "quen tin@example.com, false",
    })
    void recognisesWhatLooksLikeAnEmail(String text, boolean email) {
        assertThat(TeamService.looksLikeEmail(text)).isEqualTo(email);
    }
}
