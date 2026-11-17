package dev.irmahs.leaderboard.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.irmahs.leaderboard.accounts.AccessProperties.InitialMember;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccessPropertiesTest {

    @Test
    void recognisesAPlatformAdminWhateverTheLetterCase() {
        AccessProperties access = new AccessProperties(List.of(" Irma@Example.com ", ""), null);

        assertThat(access.isPlatformAdmin("irma@example.com")).isTrue();
        assertThat(access.isPlatformAdmin("quentin@example.com")).isFalse();
    }

    @Test
    void nothingConfiguredMeansNobody() {
        AccessProperties access = new AccessProperties(null, null);

        assertThat(access.platformAdmins()).isEmpty();
        assertThat(access.initialMembershipsOf("irma@example.com")).isEmpty();
    }

    @Test
    void readsTheInitialMembersOfOneEmail() {
        AccessProperties access = new AccessProperties(
                List.of(),
                List.of(
                        "alsace-arena:OWNER:quentin@example.com",
                        " alsace-arena : admin : Irma@Example.com ",
                        "lyon-game-bar:ADMIN:irma@example.com",
                        ""));

        assertThat(access.initialMembershipsOf("irma@example.com"))
                .containsExactly(
                        new InitialMember("alsace-arena", false, "Irma@Example.com"),
                        new InitialMember("lyon-game-bar", false, "irma@example.com"));
        assertThat(access.initialMembershipsOf("quentin@example.com"))
                .containsExactly(new InitialMember("alsace-arena", true, "quentin@example.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alsace-arena:irma@example.com", ":ADMIN:irma@example.com", "alsace-arena:ADMIN: "})
    void refusesAnEntryThatIsNotSlugRoleEmail(String entry) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccessProperties(List.of(), List.of(entry)))
                .withMessageContaining("isn't slug:OWNER:email or slug:ADMIN:email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"alsace-arena:PLAYER:irma@example.com", "alsace-arena:boss:irma@example.com"})
    void refusesARoleOtherThanOwnerOrAdmin(String entry) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AccessProperties(List.of(), List.of(entry)))
                .withMessageContaining("must say OWNER or ADMIN");
    }
}
